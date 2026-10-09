"""AnimaDev stage 2: fixed Git policy, durable single-project jobs. No network API."""
import contextlib
import fcntl
import hashlib
import json
import os
import pathlib
import re
import selectors
import shutil
import signal
import sqlite3
import subprocess
import sys
import time

VERSION = '0.4.0'
MONITOR_SHA = 'e19caf451f511782c10759b593466cc66853fe01b6999059b9c7616d8178c081'
GRADLE_SHA = 'c314d0ea3cbccce9fc0cd405a4e5e8ed3e2ec758678e5c98ea8d50ebe6faab98'
PROTOCOL = 2
HOME = pathlib.Path('/data/data/com.termux/files/home')
STATE_ROOT = HOME / '.local/share/animadev'
FIXED_GIT = str(HOME.parent / 'usr/bin/git')
FIXED_SSH = str(HOME.parent / 'usr/bin/ssh')
REGISTRY = {'animaquiz': {'name': 'AnimaQuiz', 'root': str(HOME / 'quizapp'),
    'origin': 'git@github.com:krmobdev/quizapp.git', 'branch': 'test/decomposition-v1'}}
ACTIONS = {'STATUS', 'CHECK', 'SYNC', 'TEST', 'BUILD_DEBUG'}
# Dynamic profiles are validated against catalog-derived path and GitHub rules.
DYNAMIC_REMOTE = re.compile(r'^github[.]com/([A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+)$')
DYNAMIC_BRANCH = re.compile(r'^[A-Za-z0-9_./-]{1,200}$')
DYNAMIC_DENIED = {'storage','android-sdk','sdk','bin','lib','node_modules','venv','env',
    'tmp','Downloads','Android','build','dist','target','cache','caches',
    'secrets','credentials','keystores','gradle','__pycache__'}

def validate_profile(p):
    if type(p) is not dict or set(p) != {'id','name','root','remote','branch'}:
        raise Denied('RESULT_MALFORMED')
    if not all(type(v) is str for v in p.values()): raise Denied('RESULT_MALFORMED')
    root = pathlib.Path(p['root'])
    try:
        parts = root.relative_to(HOME).parts
        if not 1 <= len(parts) <= 6 or any(not part or part.startswith('.') or
                                            part in DYNAMIC_DENIED for part in parts):
            raise ValueError()
        if root.resolve() != root.absolute() or not root.is_dir(): raise ValueError()
        marker = root / '.git'
        if marker.is_symlink() or not marker.is_dir() or marker.resolve()!=marker.absolute():
            raise ValueError()
    except (OSError, ValueError): raise Denied('PATH_DENIED')
    if p['id'] != hashlib.sha256(str(root).encode()).hexdigest()[:32]:
        raise Denied('PROJECT_NOT_FOUND')
    if not DYNAMIC_REMOTE.fullmatch(p['remote']): raise Denied('REMOTE_MISMATCH')
    branch = p['branch']
    if (not DYNAMIC_BRANCH.fullmatch(branch) or branch.startswith('-') or
            branch.endswith(('/', '.', '.lock')) or '//' in branch or '..' in branch):
        raise Denied('BRANCH_DENIED')
    if not 1 <= len(p['name']) <= 80: raise Denied('RESULT_MALFORMED')
    return {'name':p['name'],'root':str(root),'origin':p['remote'],
            'branch':branch,'dynamic':True}

TERMINAL = {'SUCCEEDED', 'FAILED', 'TIMED_OUT', 'INTERRUPTED', 'NOT_STARTED'}
HEX_ID = re.compile(r'^[0-9a-f]{32}$')
SHA = re.compile(r'^[0-9a-f]{40,64}$')
MESSAGES = {
 'ACTION_DENIED': 'Операция не разрешена.', 'PROJECT_NOT_FOUND': 'Проект не зарегистрирован.',
 'PATH_DENIED': 'Каталог проекта не соответствует разрешённому пути.',
 'GIT_MISSING': 'Git не найден в Termux.', 'REPO_MISSING': 'Локальный Git-репозиторий не найден.',
 'BRANCH_DENIED': 'Текущая ветка не разрешена. Переключение автоматически не выполняется.',
 'REMOTE_MISMATCH': 'Origin не соответствует ожидаемому репозиторию GitHub.',
 'REPO_DIRTY': 'Есть локальные изменения. Синхронизация заблокирована.',
 'LOCAL_AHEAD': 'В локальной ветке есть свои коммиты. Автосинхронизация заблокирована.',
 'NON_FAST_FORWARD': 'Истории разошлись. Безопасный fast-forward невозможен.',
 'JOB_BUSY': 'Другая операция проекта ещё выполняется.',
 'REPLAY_CONFLICT': 'ID уже принадлежит другой операции.',
 'EXECUTABLE_CONFIG': 'В Git настроены внешние фильтры или включаемые конфигурации. Автооперация заблокирована.',
 'GIT_FAILED': 'Git завершился с ошибкой. Рабочее дерево не затиралось.',
 'FETCH_FAILED': 'Не удалось получить ветку. Проверьте сеть и существующий SSH-доступ Termux.',
 'DNS_FAILED': 'Не удалось определить адрес GitHub (DNS). Проверьте подключение или VPN и повторите «Проверить обновления».',
 'NETWORK_UNAVAILABLE': 'Сеть недоступна или соединение с GitHub прервано. Проверьте подключение и повторите «Проверить обновления».',
 'SSH_AUTH_FAILED': 'GitHub отклонил SSH-авторизацию. Проверьте существующий SSH-доступ Termux; новые ключи и токены приложение не создаёт.',
 'SSH_HOST_KEY_FAILED': 'SSH не подтвердил ключ сервера. Проверка безопасности не отключалась; нужен корректный known_hosts Termux.',
 'REMOTE_ACCESS_FAILED': 'Удалённая ветка или репозиторий недоступны. Проверьте доступ и наличие согласованной ветки.',
 'TIMED_OUT': 'Git превысил лимит времени. Проверьте фактическое состояние перед повтором.',
 'JOB_INTERRUPTED': 'Процесс Termux прерван. Успех не подтверждён.',
 'RESULT_MALFORMED': 'Недопустимый запрос или ответ.', 'OUTPUT_LIMIT': 'Вывод Git превысил безопасный размер.',
 'STATE_CHANGED': 'Состояние репозитория изменилось во время операции. Обновление отменено.',
 'RUNNER_CHECKSUM_MISMATCH': 'Контрольная сумма runner не совпадает. Операция отменена.',
 'STORAGE_UNAVAILABLE': 'Не удалось сохранить состояние задания.',
 'PROCESS_IDENTITY_UNAVAILABLE': 'Termux не позволяет проверить идентичность процессов. Запуск заблокирован.',
 'HISTORY_FULL': 'Журнал достиг лимита 100 заданий. Очистка автоматически не выполняется.',
 'BUILD_OUTDATED': 'Локальный SHA устарел: обновите GitHub.',
 'BUILD_PATH_DENIED': 'Небезопасный путь APK.',
 'BUILD_ARTIFACT_MISSING': 'Gradle не создал APK.',
 'BUILD_ARTIFACT_INVALID': 'Новый APK не подтверждён.',
 'DOWNLOADS_UNAVAILABLE': 'Загрузки недоступны из Termux.',
 'ARTIFACT_EXPORT_FAILED': 'Не удалось скопировать APK в Загрузки.',
}

class Denied(Exception):
    def __init__(self, code, exit_code=None):
        super().__init__(code)
        self.code, self.exit_code = code, exit_code

def redact(value):
    text = str(value)
    text = re.sub(r'-----BEGIN [^-]*PRIVATE KEY-----.*?(?:-----END [^-]*PRIVATE KEY-----|$)', '[PRIVATE KEY REDACTED]', text, flags=re.S)
    text = re.sub(r'(?i)(authorization\s*[:=]\s*)([^\r\n]+)', r'\1[REDACTED]', text)
    text = re.sub(r'(?i)((?:token|password|passwd|secret|api[_-]?key)\s*[:=]\s*)[^\s,;]+', r'\1[REDACTED]', text)
    text = re.sub(r'\b(?:gh[pousr]_[A-Za-z0-9_]+|github_pat_[A-Za-z0-9_]+)\b', '[REDACTED]', text)
    text = re.sub(r'(https?://)[^\s/@]+:[^\s/@]+@', r'\1[REDACTED]@', text)
    text = re.sub(r'\x1b\[[0-?]*[ -/]*[@-~]', '', text)
    return text

def classify_fetch_error(stderr):
    # Recognize bounded diagnostics only; return fixed codes, never dynamic text.
    diagnostic = stderr[:65536].lower()
    if any(x in diagnostic for x in ('could not resolve hostname', 'could not resolve host:', 'temporary failure in name resolution', 'name or service not known')):
        return 'DNS_FAILED'
    if any(x in diagnostic for x in ('host key verification failed', 'remote host identification has changed')):
        return 'SSH_HOST_KEY_FAILED'
    if any(x in diagnostic for x in ('permission denied (publickey', 'authentication failed', 'no supported authentication methods', 'sign_and_send_pubkey: signing failed')):
        return 'SSH_AUTH_FAILED'
    if any(x in diagnostic for x in ('network is unreachable', 'no route to host', 'connection timed out', 'connection refused', 'connection reset', 'connection closed', 'failed to connect')):
        return 'NETWORK_UNAVAILABLE'
    if any(x in diagnostic for x in ('repository not found', "couldn't find remote ref", 'does not appear to be a git repository')):
        return 'REMOTE_ACCESS_FAILED'
    return 'FETCH_FAILED'

def secure_dir(path):
    path = pathlib.Path(path)
    path.mkdir(parents=True, exist_ok=True, mode=0o700)
    if path.resolve() != path.absolute() or not path.is_dir():
        raise Denied('PATH_DENIED')
    os.chmod(path, 0o700)
    return path

def identity(pid):
    try:
        stat_path = pathlib.Path('/proc/self/stat') if pid == os.getpid() else pathlib.Path('/proc/%s/stat' % pid)
        stat = stat_path.read_text().rsplit(')', 1)[1].split()
        if stat[0] == 'Z': return None
        boot = pathlib.Path('/proc/sys/kernel/random/boot_id').read_text().strip()
        return boot + ':' + stat[19]
    except PermissionError: return '__UNKNOWN__'
    except (OSError, IndexError): return None

class Store:
    def __init__(self, root):
        self.root = secure_dir(root)
        self.db = self.root / 'jobs.sqlite3'
        if self.db.is_symlink(): raise Denied('PATH_DENIED')
        with self.transaction() as c:
            c.execute('CREATE TABLE IF NOT EXISTS jobs (id TEXT PRIMARY KEY, project TEXT NOT NULL, fingerprint TEXT NOT NULL, data TEXT NOT NULL)')
        os.chmod(self.db, 0o600)
    def connect(self):
        c = sqlite3.connect(self.db, timeout=5)
        c.execute('PRAGMA synchronous=FULL')
        return c
    @contextlib.contextmanager
    def transaction(self):
        c = self.connect()
        try:
            c.execute('BEGIN IMMEDIATE')
            yield c
            c.commit()
        except BaseException:
            c.rollback()
            raise
        finally: c.close()
    def get(self, c, jid):
        row = c.execute('SELECT data FROM jobs WHERE id=?', (jid,)).fetchone()
        return json.loads(row[0]) if row else None
    def save(self, c, job):
        c.execute('INSERT OR REPLACE INTO jobs VALUES(?,?,?,?)',
            (job['job_id'], job['project_id'], job['fingerprint'], json.dumps(job, ensure_ascii=True)))
    def update(self, jid, **changes):
        with self.transaction() as c:
            job = self.get(c, jid)
            job.update(changes)
            self.save(c, job)
        return job
    def reconcile(self, c):
        for row in c.execute('SELECT data FROM jobs').fetchall():
            j = json.loads(row[0])
            active = j['state'] not in TERMINAL
            current = identity(j['pid']) if j.get('pid') else None
            dead = j.get('pid') and current != '__UNKNOWN__' and current != j.get('process_identity')
            unstarted = not j.get('pid') and time.time() - j['created_at'] > 30
            child = identity(j['child_pid']) if j.get('child_pid') else None
            child_live = child is not None and (child == '__UNKNOWN__' or j.get('child_identity') is None or child == j.get('child_identity'))
            if active and (current == '__UNKNOWN__' or (dead and child_live)):
                j.update(state='UNKNOWN', message='Контроллер недоступен; дочерний процесс может ещё выполняться. Повторный запуск заблокирован.')
                self.save(c, j)
                continue
            if active and j.get('action') in ('TEST','BUILD_DEBUG') and (dead or j.get('unresolved')):
                j.update(state='UNKNOWN', unresolved=True, message='Контроллер Gradle потерян или задача превысила таймаут. Результат неизвестен; новый запуск заблокирован.')
                self.save(c, j)
                continue
            if active and (dead or unstarted):
                j.update(state='INTERRUPTED', ended_at=time.time(), error_code='JOB_INTERRUPTED',
                    message=MESSAGES['JOB_INTERRUPTED'], exit_code=None)
                self.save(c, j)
    def latest(self, project):
        with self.transaction() as c:
            self.reconcile(c)
            jobs = [json.loads(x[0]) for x in c.execute('SELECT data FROM jobs WHERE project=?', (project,))]
            return max(jobs, key=lambda j:j['created_at']) if jobs else None

def fingerprint(project, action):
    return hashlib.sha256((project + ':' + action).encode()).hexdigest()

class GitGuard:
    def __init__(self, project, log=lambda *a:None, git_executable=FIXED_GIT, on_process=lambda *a:None):
        self.project = project
        self.root = pathlib.Path(project['root'])
        self.log = log
        self.on_process = on_process
        self.git = shutil.which(git_executable)
        if not self.git: raise Denied('GIT_MISSING')
        if not self.root.is_dir(): raise Denied('REPO_MISSING')
        if self.root.resolve() != self.root.absolute(): raise Denied('PATH_DENIED')
        self.env = {k:v for k,v in os.environ.items() if not k.startswith('GIT_')}
        self.env.update(LC_ALL='C', GIT_CONFIG_NOSYSTEM='1', GIT_CONFIG_GLOBAL=os.devnull,
            GIT_TERMINAL_PROMPT='0', GIT_OPTIONAL_LOCKS='0', GIT_PAGER='cat',
            GIT_SSH_COMMAND=FIXED_SSH+' -oBatchMode=yes -oStrictHostKeyChecking=yes -oConnectTimeout=15')
        self.prefix = [self.git, '-c', 'core.hooksPath=' + os.devnull, '-c', 'core.fsmonitor=false',
            '-c', 'core.untrackedCache=false', '-c', 'gc.auto=0', '-c', 'maintenance.auto=false',
            '-c', 'submodule.recurse=false', '-c', 'merge.autoStash=false', '-c', 'commit.gpgSign=false',
            '-c', 'core.pager=cat', '-c', 'diff.external=']
        self.config_guard()
    def config_guard(self):
        # Config names only: never expose values or credentials. No includes followed.
        code, names, _ = self.call(['config', '--local', '--no-includes', '--name-only', '--get-regexp',
            r'^(filter\.|include\.|includeif\.|core\.gitproxy|remote\..*\.(vcs|uploadpack)|url\.)'], allow_failure=True)
        if code == 0 and names.strip(): raise Denied('EXECUTABLE_CONFIG')
        if code not in (0, 1): raise Denied('REPO_MISSING', code)
    def call(self, args, timeout=30, allow_failure=False):
        self.log('command', __import__('shlex').join(self.prefix + args))
        self.log('step', args[0])
        # Permission gate: child cannot exec Git before its identity is durably recorded.
        gate = "import os,sys\npermit=sys.stdin.buffer.read(1)\nif permit!=b'1': sys.exit(1)\nos.execvpe(sys.argv[1],sys.argv[1:],os.environ)"
        p = subprocess.Popen([sys.executable, '-c', gate] + self.prefix + args, cwd=self.root, env=self.env,
            stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE, start_new_session=True)
        selector = selectors.DefaultSelector()
        selector.register(p.stdout, selectors.EVENT_READ, 'stdout')
        selector.register(p.stderr, selectors.EVENT_READ, 'stderr')
        buffers = {'stdout': bytearray(), 'stderr': bytearray()}
        deadline = time.monotonic() + timeout
        try:
            self.on_process(p.pid, identity(p.pid))
            p.stdin.write(b'1'); p.stdin.flush(); p.stdin.close()
            while selector.get_map():
                if hasattr(self, 'observe'): self.observe()
                if time.monotonic() >= deadline: raise Denied('TIMED_OUT')
                for key, _ in selector.select(0.1):
                    data = os.read(key.fileobj.fileno(), 4096)
                    if not data:
                        selector.unregister(key.fileobj)
                        continue
                    buffers[key.data].extend(data)
                    if len(buffers[key.data]) > 65536: raise Denied('OUTPUT_LIMIT')
            code = p.wait(timeout=max(.1, deadline-time.monotonic()))
        except BaseException:
            os.killpg(p.pid, signal.SIGKILL); p.wait()
            raise
        finally:
            selector.close(); p.stdout.close(); p.stderr.close()
            if not p.stdin.closed: p.stdin.close()
            self.on_process(None, None)
        out = bytes(buffers['stdout']).decode('utf-8', 'replace')
        err = bytes(buffers['stderr']).decode('utf-8', 'replace')
        # Only sanitized stderr and fixed step names enter the durable journal.
        if out: self.log('stdout', redact(out)+'\n')
        if err: self.log('stderr', redact(err)+'\n')
        self.log('event', 'Git '+args[0]+' завершён; exit='+str(code))
        if code and not allow_failure: raise Denied('GIT_FAILED', code)
        return code, out, err
    def text(self, args): return self.call(args)[1].strip()
    def snapshot(self):
        self.config_guard()
        top = self.text(['rev-parse', '--show-toplevel'])
        if pathlib.Path(top).resolve() != self.root: raise Denied('PATH_DENIED')
        branch = self.text(['symbolic-ref', '--quiet', '--short', 'HEAD'])
        sha = self.text(['rev-parse', '--verify', 'HEAD^{commit}'])
        if not SHA.fullmatch(sha): raise Denied('RESULT_MALFORMED')
        # Read literal origin rather than get-url (URL rewrites are forbidden above).
        code, remote, _ = self.call(['config', '--local', '--no-includes', '--get-all', 'remote.origin.url'], allow_failure=True)
        remote_ok = code == 0 and remote.splitlines() == [self.project['origin']]
        changes = self.call(['status', '--porcelain=v1', '-z', '--untracked-files=all'])[1]
        names = [redact(x)[:200] for x in changes.split('\0') if x][:20]
        allowed = branch == self.project['branch'] and branch not in ('main', 'master')
        return {'name': self.project['name'], 'branch': branch, 'local_commit': sha,
            'dirty': bool(changes), 'changed_files': names, 'remote_ok': remote_ok,
            'branch_allowed': allowed, 'remote_commit': '', 'ahead': 0, 'behind': 0,
            'can_sync': False, 'checked': False}
    def require(self, s, clean=False):
        if not s['branch_allowed']: raise Denied('BRANCH_DENIED')
        if not s['remote_ok']: raise Denied('REMOTE_MISMATCH')
        if clean and s['dirty']: raise Denied('REPO_DIRTY')
    def compare(self, s, target):
        if not SHA.fullmatch(target): raise Denied('RESULT_MALFORMED')
        counts = self.text(['rev-list', '--left-right', '--count', s['local_commit']+'...'+target]).split()
        ahead, behind = map(int, counts)
        code = self.call(['merge-base', '--is-ancestor', s['local_commit'], target], allow_failure=True)[0]
        if code not in (0, 1): raise Denied('GIT_FAILED', code)
        return dict(s, remote_commit=target, ahead=ahead, behind=behind, checked=True,
            can_sync=not s['dirty'] and s['branch_allowed'] and s['remote_ok'] and ahead==0 and behind>0 and code==0)
    def fetch(self, jid):
        ref = 'refs/animadev/' + jid
        # URL and refspec are fixed registry data, no configured helper or arbitrary remote.
        code, _, diagnostic = self.call(['fetch', '--no-tags', '--no-recurse-submodules', '--no-write-fetch-head',
            self.project['origin'], 'refs/heads/'+self.project['branch']+':'+ref], timeout=180, allow_failure=True)
        if code: raise Denied(classify_fetch_error(diagnostic), code)
        return self.text(['rev-parse', '--verify', ref+'^{commit}'])
    def execute(self, action, jid):
        before = self.snapshot()
        if action == 'STATUS': return before
        self.require(before, clean=action=='SYNC')
        target = self.fetch(jid)
        current = self.snapshot()
        self.require(current, clean=action=='SYNC')
        if current['branch'] != before['branch'] or current['local_commit'] != before['local_commit']:
            raise Denied('STATE_CHANGED')
        result = self.compare(current, target)
        if action == 'CHECK': return result
        if result['ahead'] and result['behind']: raise Denied('NON_FAST_FORWARD')
        if result['ahead']: raise Denied('LOCAL_AHEAD')
        if not result['behind']: return result
        if not result['can_sync']: raise Denied('NON_FAST_FORWARD')
        final_guard = self.snapshot()
        self.require(final_guard, clean=True)
        if final_guard['local_commit'] != current['local_commit']: raise Denied('STATE_CHANGED')
        self.call(['merge', '--ff-only', '--no-edit', '--no-stat', target], timeout=60)
        final = self.snapshot()
        if final['local_commit'] != target or final['branch'] != before['branch'] or final['dirty']:
            raise Denied('STATE_CHANGED')
        return self.compare(final, target)

class Engine:
    def __init__(self, root=STATE_ROOT, registry=None):
        self.store = Store(root)
        self.registry = REGISTRY if registry is None else registry
        self.git_executable = FIXED_GIT if registry is None else shutil.which('git')
        module_path = pathlib.Path(__file__).resolve().parent / 'job_monitor.py'
        data = module_path.read_bytes()
        if module_path.is_symlink() or hashlib.sha256(data).hexdigest() != MONITOR_SHA: raise Denied('RUNNER_CHECKSUM_MISMATCH')
        self.monitor = {'__file__':str(module_path), '__name__':'verified_job_monitor'}
        exec(compile(data,str(module_path),'exec'), self.monitor)
        self.journal = self.monitor['Journal'](self.store, redact)
    def validate(self, jid, project, action):
        if not HEX_ID.fullmatch(jid): raise Denied('RESULT_MALFORMED')
        if project not in self.registry: raise Denied('PROJECT_NOT_FOUND')
        if action not in ACTIONS: raise Denied('ACTION_DENIED')
    def reserve(self, jid, project, action, reconcile=False):
        self.validate(jid, project, action)
        fp = fingerprint(project, action)
        with self.store.transaction() as c:
            self.store.reconcile(c)
            prior = self.store.get(c, jid)
            if prior:
                if prior['fingerprint'] != fp: raise Denied('REPLAY_CONFLICT')
                return prior, False
            if not reconcile:
                active = [json.loads(x[0]) for x in c.execute('SELECT data FROM jobs WHERE project=?', (project,))]
                if any(x['state'] not in TERMINAL for x in active): raise Denied('JOB_BUSY')
            if c.execute('SELECT COUNT(*) FROM jobs').fetchone()[0] >= 100:
                raise Denied('HISTORY_FULL')
            j = {'job_id': jid, 'project_id': project, 'action': action, 'fingerprint': fp,
                'state': 'NOT_STARTED' if reconcile else 'QUEUED', 'created_at': time.time(),
                'started_at': None, 'ended_at': time.time() if reconcile else None,
                'pid': None, 'process_identity': None, 'child_pid': None, 'child_identity': None, 'exit_code': None, 'error_code': '',
                'message': 'Не запускалась: отложенный START с этим ID заблокирован.' if reconcile else 'Задание принято.',
                'snapshot': None, 'stdout': '', 'stderr': ''}
            self.store.save(c, j)
            c.execute('INSERT OR IGNORE INTO journal_meta(job) VALUES(?)',(jid,))
            return j, not reconcile
    def start(self, jid, project, action, spawn=True):
        self.validate(jid, project, action)
        if identity(os.getpid()) in (None, '__UNKNOWN__'): raise Denied('PROCESS_IDENTITY_UNAVAILABLE')
        j, new = self.reserve(jid, project, action)
        if new and spawn:
            try:
                worker_path = str(pathlib.Path(__file__).resolve())
                expected = globals().get('EXPECTED_SHA') or hashlib.sha256(pathlib.Path(worker_path).read_bytes()).hexdigest()
                launcher = "import pathlib,hashlib,sys; p=pathlib.Path(sys.argv[1]); d=p.read_bytes(); assert p.resolve()==p.absolute() and hashlib.sha256(d).hexdigest()==sys.argv[2]; h=sys.argv[2]; sys.argv=[str(p),'--worker',sys.argv[3]]; exec(compile(d,str(p),'exec'),{'__name__':'__main__','__file__':str(p),'EXPECTED_SHA':h})"
                p = subprocess.Popen([sys.executable, '-c', launcher, worker_path, expected, jid],
                    stdin=subprocess.DEVNULL, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
                    start_new_session=True, close_fds=True)
                # Worker may already have stored its identity/final result: do not replace them.
                with self.store.transaction() as c:
                    latest = self.store.get(c, jid)
                    if latest['state'] == 'QUEUED':
                        latest.update(pid=p.pid, process_identity=identity(p.pid))
                        self.store.save(c, latest)
                    j = latest
            except OSError:
                j = self.store.update(jid, state='FAILED', error_code='STORAGE_UNAVAILABLE',
                    ended_at=time.time(), message=MESSAGES['STORAGE_UNAVAILABLE'])
        return j
    def query(self, jid, project, action):
        # Tombstone absent requests atomically. A delayed START can never execute afterwards.
        return self.reserve(jid, project, action, reconcile=True)[0]
    def run_job(self, jid):
        with self.store.transaction() as c:
            j = self.store.get(c, jid)
            if not j or j['state'] != 'QUEUED': return
            j.update(state='RUNNING', pid=os.getpid(), process_identity=identity(os.getpid()), started_at=time.time())
            self.store.save(c, j)
        lock_path = self.store.root / ('project-' + j['project_id'] + '.lock')
        if lock_path.is_symlink():
            self.store.update(jid, state='FAILED', error_code='PATH_DENIED', ended_at=time.time())
            return
        fd = os.open(lock_path, os.O_CREAT|os.O_RDWR|os.O_NOFOLLOW, 0o600)
        guard = None
        signals = self.monitor['Signals'](self.store,jid,self.registry[j['project_id']]['root'])
        def log(channel, content):
            safe = self.journal.write(jid,channel,content if str(content).endswith('\n') else str(content)+'\n',channel not in ('stdout',)) or ''
            if channel in ('stdout','stderr'): signals.output(content)
            if channel=='step': self.store.update(jid,stage='Git: '+str(content)[:180])
            # Legacy wire tails are sanitized independently and intentionally small.
            if channel not in ('stdout','stderr'): return
            with self.store.transaction() as c:
                row=self.store.get(c,jid); row[channel]=(row.get(channel,'')+'\n'+safe)[-4096:]
                self.store.save(c,row)
        log('event','Запуск '+j['action']+' · AnimaQuiz · Job ID '+jid)
        log('event','Рабочий каталог: '+self.registry[j['project_id']]['root'])
        self.store.update(jid,last_progress_at=time.time(),stage='Проверка проекта')
        try:
            try: fcntl.flock(fd, fcntl.LOCK_EX|fcntl.LOCK_NB)
            except BlockingIOError: raise Denied('JOB_BUSY')
            guard = GitGuard(self.registry[j['project_id']], log, self.git_executable,
                lambda pid, token: self.store.update(jid, child_pid=pid, child_identity=token))
            guard.observe = signals.tick
            if j['action'] in ('TEST','BUILD_DEBUG'):
                module_path = pathlib.Path(__file__).resolve().parent / 'gradle_tests.py'
                data = module_path.read_bytes()
                if module_path.is_symlink() or hashlib.sha256(data).hexdigest() != GRADLE_SHA:
                    raise Denied('RUNNER_CHECKSUM_MISMATCH')
                module = {'__file__':str(module_path), '__name__':'verified_gradle_tests'}
                exec(compile(data,str(module_path),'exec'), module)
                result = (module['execute'](guard,log,
                    lambda pid, token:self.store.update(jid,child_pid=pid,child_identity=token),identity,redact)
                    if j['action']=='TEST' else module['execute_debug'](guard,log,
                    lambda pid, token:self.store.update(jid,child_pid=pid,child_identity=token),identity,redact,jid))
                code = result.get('error_code','')
                result['message'] = MESSAGES.get(code,module['MESSAGES'].get(code,'Debug APK готов.' if j['action']=='BUILD_DEBUG' else 'Unit-тесты завершены.'))
                self.store.update(jid,ended_at=time.time(),**result)
                return
            snap = guard.execute(j['action'], jid)
            message = 'Состояние репозитория проверено.'
            if snap['checked']:
                message = 'Есть обновления: %s коммитов.' % snap['behind'] if snap['behind'] else 'Новых удалённых коммитов нет.'
            if j['action']=='SYNC': message = 'Синхронизация завершена.' if snap['local_commit'] else 'Уже актуально.'
            self.store.update(jid, state='SUCCEEDED', exit_code=0, ended_at=time.time(), snapshot=snap, message=message)
        except Denied as e:
            snap = None
            if guard:
                try: snap = guard.snapshot()
                except Exception: pass
            self.store.update(jid, state='TIMED_OUT' if e.code=='TIMED_OUT' else 'FAILED',
                error_code=e.code, message=MESSAGES.get(e.code, 'Ошибка Git.'), exit_code=e.exit_code,
                ended_at=time.time(), snapshot=snap)
        except Exception:
            if j['action'] in ('TEST','BUILD_DEBUG'):
                self.store.update(jid,state='UNKNOWN',unresolved=True,error_code='RESULT_MALFORMED',message='Результат Gradle не подтверждён; повторный запуск заблокирован.',ended_at=time.time())
                return
            self.store.update(jid, state='FAILED', error_code='STORAGE_UNAVAILABLE',
                message=MESSAGES['STORAGE_UNAVAILABLE'], ended_at=time.time())
        finally:
            try:
                final=self.store.update(jid,computing=False)
                log('event','Итог: '+final['state']+' · exit='+str(final.get('exit_code'))+' · '+final.get('message',''))
                self.journal.flush(jid)
            finally: os.close(fd)

def wire_job(job):
    result=dict(job)
    result['stdout']=redact(job.get('stdout',''))[-1500:]
    result['stderr']=redact(job.get('stderr',''))[-1500:]
    result['message']=redact(job.get('message',''))[:400]
    if job.get('snapshot'):
        result['snapshot']=dict(job['snapshot'])
        result['snapshot']['changed_files']=[x[:120] for x in job['snapshot']['changed_files'][:10]]
    return result

# CLI has no fixture configuration, shell, path, remote, branch or command parameters.
def main():
    os.umask(0o077)
    if len(sys.argv)==3 and sys.argv[1]=='--worker':
        jid=sys.argv[2]
        if not HEX_ID.fullmatch(jid): return 1
        Engine().run_job(jid)
        return 0
    request_id = ''
    try:
        if len(sys.argv)!=2 or len(sys.argv[1])>2048: raise Denied('RESULT_MALFORMED')
        p=json.loads(sys.argv[1])
        keys={'request_id','mode','job_id','project_id','action'}
        if p.get('mode') in ('LOG','HISTORY'): keys |= {'offset','view'}
        if set(p) != keys: raise Denied('RESULT_MALFORMED')
        request_id=p['request_id']
        if not isinstance(request_id,str) or not HEX_ID.fullmatch(request_id): raise Denied('RESULT_MALFORMED')
        engine=Engine()
        if p['mode'] in ('LOG','HISTORY'):
            engine.validate(p['job_id'],p['project_id'],p['action'])
            if p['mode']=='HISTORY': result=engine.journal.history(p['project_id'],p['offset'])
            else:
                with engine.store.transaction() as c: existing=engine.store.get(c,p['job_id'])
                if not existing or existing['project_id']!=p['project_id'] or existing['action']!=p['action']: raise Denied('RESULT_MALFORMED')
                result=engine.journal.page(existing,p['offset'],p['view'])
            print(json.dumps({'protocol_version':PROTOCOL,'runner_version':VERSION,'request_id':request_id,'ok':True,'page':result},ensure_ascii=False))
            return 0
        if p['mode']=='START': job=engine.start(p['job_id'],p['project_id'],p['action'])
        elif p['mode']=='QUERY': job=engine.query(p['job_id'],p['project_id'],p['action'])
        else: raise Denied('ACTION_DENIED')
        print(json.dumps({'protocol_version':PROTOCOL,'runner_version':VERSION,'request_id':request_id,
            'ok':True,'job':wire_job(job)},ensure_ascii=False))
        return 0
    except Denied as e:
        reply={'protocol_version':PROTOCOL,'runner_version':VERSION,'request_id':request_id,
            'ok':False,'error_code':e.code,'message':MESSAGES.get(e.code,'Запрос отклонён.')}
        if e.code=='JOB_BUSY':
            try: active=engine.store.latest(p['project_id']); reply['active_job']=wire_job(active) if active else None
            except Exception: pass
        print(json.dumps(reply,ensure_ascii=False))
        return 0
    except Exception:
        print(json.dumps({'protocol_version':PROTOCOL,'runner_version':VERSION,'request_id':request_id,
            'ok':False,'error_code':'STORAGE_UNAVAILABLE','message':MESSAGES['STORAGE_UNAVAILABLE']}))
        return 1

if __name__=='__main__': sys.exit(main())
