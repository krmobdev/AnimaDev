"""Fixed Gradle TEST operation. Imported only after SHA verification by runner."""
import hashlib
import math
import os
import pathlib
import re
import selectors
import signal
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

COMMAND = ('./gradlew', ':app:testDebugUnitTest', '--offline', '--no-daemon', '--rerun-tasks', '--console=plain')
TIMEOUT = 5400
MAX_XML = 2 * 1024 * 1024
MAX_FILES = 500
MESSAGES = {
 'GRADLE_MISSING': 'В проекте не найден обычный файл gradlew.',
 'GRADLE_FAILED': 'Gradle завершился с ошибкой. Проверьте диагностику: возможны недоступные офлайн-зависимости или ошибка проекта.',
 'TESTS_FAILED': 'Есть проваленные unit-тесты. Посмотрите результаты и диагностику.',
 'REPORTS_MISSING': 'Новые JUnit XML не получены. Успех тестов не подтверждён.',
 'REPORTS_INVALID': 'Отчёт JUnit повреждён или превышает безопасный размер. Успех не подтверждён.',
 'TEST_TIMEOUT_UNKNOWN': 'Превышен лимит 90 минут. Gradle мог оставить дочерние процессы; результат неизвестен, повтор заблокирован.',
 'TEST_STATE_CHANGED': 'Ветка или коммит изменились во время тестов. Результат не подтверждён.',
 'RUNNER_CHECKSUM_MISMATCH': 'Контрольная сумма модуля тестов не совпадает. Запуск отменён.',
}

class ReportError(Exception):
    def __init__(self, code): self.code = code

def reports(root):
    directory = pathlib.Path(root) / 'app/build/test-results/testDebugUnitTest'
    if directory.resolve() != directory.absolute(): raise ReportError('REPORTS_INVALID')
    if not directory.exists(): return {}
    files = sorted(directory.glob('TEST-*.xml'))
    if len(files) > MAX_FILES: raise ReportError('REPORTS_INVALID')
    result = {}
    total_bytes = 0
    for path in files:
        if path.is_symlink() or not path.is_file(): raise ReportError('REPORTS_INVALID')
        stat = path.stat()
        if stat.st_size > MAX_XML: raise ReportError('REPORTS_INVALID')
        total_bytes += stat.st_size
        if total_bytes > 8 * 1024 * 1024: raise ReportError('REPORTS_INVALID')
        data = path.read_bytes()
        if len(data) > MAX_XML: raise ReportError('REPORTS_INVALID')
        result[path.name] = ((stat.st_ino, stat.st_mtime_ns, stat.st_ctime_ns, hashlib.sha256(data).hexdigest()), data)
    return result

def summary(before, after):
    fresh = [data for name, (stamp, data) in after.items() if name not in before or before[name][0] != stamp]
    if not fresh: raise ReportError('REPORTS_MISSING')
    total = failed = skipped = 0
    duration = 0.0
    for data in fresh:
        if b'<!DOCTYPE' in data.upper() or b'<!ENTITY' in data.upper(): raise ReportError('REPORTS_INVALID')
        try:
            suite = ET.fromstring(data)
            if suite.tag != 'testsuite': raise ValueError()
            cases = suite.findall('testcase')
            n = int(suite.attrib['tests']); f = int(suite.attrib.get('failures', '0')); e = int(suite.attrib.get('errors', '0')); s = int(suite.attrib.get('skipped', '0'))
            d = float(suite.attrib['time'])
            cf = sum(case.find('failure') is not None or case.find('error') is not None for case in cases)
            cs = sum(case.find('skipped') is not None for case in cases)
            if n != len(cases) or cf != f + e or cs != s or cf + cs > n or min(n,f,e,s,d) < 0 or not math.isfinite(d): raise ValueError()
            total += n; failed += cf; skipped += cs; duration += d
        except (ET.ParseError, KeyError, ValueError, OverflowError): raise ReportError('REPORTS_INVALID')
    return {'total':total, 'passed':total-failed-skipped, 'failed':failed, 'skipped':skipped,
            'duration_ms':round(duration*1000), 'report_files':len(fresh)}

def diagnostic(text, redact):
    text = redact(text)
    # Keep useful Gradle/JUnit messages, omit Java/Python stack frames.
    lines = [line for line in text.splitlines() if not re.match(r'\s*(at\s+\S+\(|File \"|Traceback)', line)]
    return '\n'.join(lines)[-4096:]

class SafeLines:
    def __init__(self, redact): self.redact=redact; self.private=False; self.suppressed=False
    def feed(self, text):
        if self.suppressed: return ''
        begins=re.search(r'-----BEGIN [^-]*PRIVATE KEY-----',text)
        if begins:self.private=True
        if self.private:
            if re.search(r'-----END [^-]*PRIVATE KEY-----',text):self.private=False
            return '[PRIVATE KEY REDACTED]' if begins else ''
        return diagnostic(text,self.redact)
    def oversized(self):
        # A truncated line may contain a partial secret header: suppress remaining channel.
        self.suppressed=True
        return '[Длинная строка диагностики пропущена; дальнейший вывод канала скрыт]'

def process(root, env, log, on_process, identity, redact, timeout=TIMEOUT, observe=lambda:None, command=COMMAND):
    # A permission gate records the child before it may execute the fixed command.
    gate = "import os,sys\nif sys.stdin.buffer.read(1)!=b'1': sys.exit(1)\nos.execvpe(sys.argv[1],sys.argv[1:],os.environ)"
    bash = '/data/data/com.termux/files/usr/bin/bash' if sys.platform == 'android' else '/bin/bash'
    p = subprocess.Popen([sys.executable,'-c',gate,bash,*command], cwd=root, env=env,
        stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.PIPE,start_new_session=True)
    selector = selectors.DefaultSelector()
    selector.register(p.stdout,selectors.EVENT_READ,'stdout'); selector.register(p.stderr,selectors.EVENT_READ,'stderr')
    fragments = {'stdout':bytearray(),'stderr':bytearray()}; tails={'stdout':'','stderr':''}
    dropping={'stdout':False,'stderr':False}
    filters={channel:SafeLines(redact) for channel in tails}
    deadline=time.monotonic()+timeout; last_save=0.0; interrupt_sent=False
    def save(force=False):
        nonlocal last_save
        if force or time.monotonic()-last_save>.5:
            for channel in tails:
                if tails[channel]: log(channel,diagnostic(tails[channel],redact))
            tails.update(stdout='',stderr='');last_save=time.monotonic()
    def consume(channel,data):
        for piece in data.splitlines(keepends=True):
            if not dropping[channel]: fragments[channel].extend(piece)
            if len(fragments[channel])>8192:
                fragments[channel].clear();dropping[channel]=True
            if piece.endswith(b'\n'):
                if dropping[channel]: line=filters[channel].oversized()+'\n'
                else: line=filters[channel].feed(fragments[channel].decode('utf-8','replace'))+'\n'
                if line.strip(): log(channel,line)
                fragments[channel].clear();dropping[channel]=False
    try:
        initial_identity=identity(p.pid)
        on_process(p.pid,initial_identity);p.stdin.write(b'1');p.stdin.flush();p.stdin.close()
        while selector.get_map():
            # The controller checks a durable one-shot cancel flag. Gradle
            # receives Ctrl+C via SIGINT, never a signal to an unchecked PID.
            if observe() and not interrupt_sent:
                if identity(p.pid)!=initial_identity:
                    return None,True
                try:
                    os.killpg(p.pid,signal.SIGINT)
                    interrupt_sent=True
                    log('event','Отмена: Gradle получил Ctrl+C (SIGINT).')
                except ProcessLookupError:
                    interrupt_sent=True
            if time.monotonic()>=deadline:
                try: os.killpg(p.pid,signal.SIGKILL)
                except ProcessLookupError: pass
                p.wait();save(True)
                return None,True
            for key,_ in selector.select(.1):
                chunk=os.read(key.fileobj.fileno(),4096)
                if chunk:consume(key.data,chunk)
                else:selector.unregister(key.fileobj)
            save()
        for channel in fragments:
            if fragments[channel] and not dropping[channel]:tails[channel]=(tails[channel]+filters[channel].feed(fragments[channel].decode('utf-8','replace')))[-4096:]
        save(True)
        return p.wait(timeout=max(.1,deadline-time.monotonic())),False
    except BaseException:
        try:os.killpg(p.pid,signal.SIGKILL)
        except ProcessLookupError:pass
        p.wait();raise
    finally:
        selector.close();p.stdout.close();p.stderr.close()
        if not p.stdin.closed:p.stdin.close()
        on_process(None,None)

def execute(guard, log, on_process, identity, redact, timeout=TIMEOUT, jid=''):
    snapshot=guard.snapshot();guard.require(snapshot, clean=bool(guard.project.get('dynamic')))
    if guard.project.get('dynamic'):
        if not jid: return dict(state='FAILED',error_code='TEST_STATE_CHANGED',exit_code=None,snapshot=snapshot,tests=None)
        remote=guard.fetch(jid)
        checked=guard.snapshot();guard.require(checked,clean=True)
        if snapshot['branch'] != checked['branch'] or snapshot['local_commit'] != checked['local_commit']:
            return dict(state='FAILED',error_code='TEST_STATE_CHANGED',exit_code=None,snapshot=checked,tests=None)
        current=guard.compare(checked,remote)
        if current['local_commit'] != remote or current['ahead'] or current['behind']:
            return dict(state='FAILED',error_code='BUILD_OUTDATED',exit_code=None,snapshot=current,tests=None)
        snapshot=current
    wrapper=guard.root/'gradlew'
    if not wrapper.is_file() or wrapper.is_symlink():return dict(state='FAILED',error_code='GRADLE_MISSING',exit_code=None,snapshot=snapshot,tests=None)
    try:before=reports(guard.root)
    except ReportError as e:return dict(state='FAILED',error_code=e.code,exit_code=None,snapshot=snapshot,tests=None)
    env=dict(guard.env)
    # Avoid environment-provided arbitrary Gradle init options; retain established Java/SDK paths.
    for key in ('JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','_JAVA_OPTIONS','GRADLE_OPTS','JAVA_OPTS'):env.pop(key,None)
    log('command','bash ./gradlew :app:testDebugUnitTest --offline --no-daemon --rerun-tasks --console=plain')
    code,unknown=process(guard.root,env,log,on_process,identity,redact,timeout,getattr(guard,'observe',lambda:None))
    if unknown:return dict(state='UNKNOWN',error_code='TEST_TIMEOUT_UNKNOWN',exit_code=None,snapshot=snapshot,tests=None,unresolved=True)
    result=dict(state='FAILED',error_code='GRADLE_FAILED',exit_code=code,snapshot=snapshot,tests=None)
    try:result['tests']=summary(before,reports(guard.root))
    except ReportError as e:
        if code==0:result['error_code']=e.code
    after=guard.snapshot();guard.require(after,clean=bool(guard.project.get('dynamic')))
    result['snapshot']=after
    if after['branch']!=snapshot['branch'] or after['local_commit']!=snapshot['local_commit']:
        result['error_code']='TEST_STATE_CHANGED'
    elif result['tests'] and result['tests']['failed']:
        result['error_code']='TESTS_FAILED'
    elif code==0 and result['tests'] is not None:
        result.update(state='SUCCEEDED',error_code='')
    return result


DEBUG_COMMAND = ('./gradlew', ':app:assembleDebug', '--offline', '--no-daemon', '--rerun-tasks', '--console=plain')
DEBUG_APK = 'app/build/outputs/apk/debug/app-debug.apk'
DOWNLOADS = pathlib.Path('/storage/emulated/0/Download')

def execute_debug(guard, log, on_process, identity, redact, jid, timeout=TIMEOUT):
    """Build only clean exact GitHub SHA and persist a verified fresh APK."""
    import zipfile
    initial = guard.snapshot()
    guard.require(initial, clean=True)
    remote = guard.fetch(jid)
    checked = guard.snapshot()
    guard.require(checked, clean=True)
    if checked['branch'] != initial['branch'] or checked['local_commit'] != initial['local_commit']:
        return dict(state='FAILED', error_code='TEST_STATE_CHANGED', exit_code=None, snapshot=checked)
    checked = guard.compare(checked, remote)
    if checked['local_commit'] != remote or checked['ahead'] or checked['behind']:
        return dict(state='FAILED', error_code='BUILD_OUTDATED', exit_code=None, snapshot=checked)
    wrapper = guard.root / 'gradlew'
    if not wrapper.is_file() or wrapper.is_symlink():
        return dict(state='FAILED', error_code='GRADLE_MISSING', exit_code=None, snapshot=checked)
    apk = guard.root / DEBUG_APK
    if apk.is_symlink() or apk.parent.resolve() != apk.parent.absolute():
        return dict(state='FAILED', error_code='BUILD_PATH_DENIED', exit_code=None, snapshot=checked)
    stamp = None
    if apk.exists():
        if not apk.is_file(): return dict(state='FAILED', error_code='BUILD_PATH_DENIED', exit_code=None, snapshot=checked)
        s = apk.stat()
        stamp = (s.st_ino, s.st_size, s.st_mtime_ns, s.st_ctime_ns)
    env = dict(guard.env)
    for key in ('JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','_JAVA_OPTIONS','GRADLE_OPTS','JAVA_OPTS'): env.pop(key,None)
    log('event','Проверен GitHub SHA '+remote[:12])
    log('command','bash ./gradlew :app:assembleDebug --offline --no-daemon --rerun-tasks --console=plain')
    code, unknown = process(guard.root,env,log,on_process,identity,redact,
                            timeout,getattr(guard,'observe',lambda:None),DEBUG_COMMAND)
    if unknown: return dict(state='UNKNOWN',unresolved=True,error_code='TEST_TIMEOUT_UNKNOWN',exit_code=None,snapshot=checked)
    after = guard.snapshot()
    guard.require(after,clean=True)
    if after['branch'] != checked['branch'] or after['local_commit'] != remote:
        return dict(state='FAILED',error_code='TEST_STATE_CHANGED',exit_code=code,snapshot=after)
    if code != 0: return dict(state='FAILED',error_code='GRADLE_FAILED',exit_code=code,snapshot=after)
    if not apk.is_file() or apk.is_symlink() or apk.parent.resolve() != apk.parent.absolute():
        return dict(state='FAILED',error_code='BUILD_ARTIFACT_MISSING',exit_code=code,snapshot=after)
    s = apk.stat()
    if s.st_size < 1024 or s.st_size > 512*1024*1024 or stamp == (s.st_ino,s.st_size,s.st_mtime_ns,s.st_ctime_ns):
        return dict(state='FAILED',error_code='BUILD_ARTIFACT_INVALID',exit_code=code,snapshot=after)
    try:
        with zipfile.ZipFile(apk) as z:
            if 'AndroidManifest.xml' not in z.namelist(): raise ValueError()
    except (OSError,ValueError,zipfile.BadZipFile):
        return dict(state='FAILED',error_code='BUILD_ARTIFACT_INVALID',exit_code=code,snapshot=after)
    if not DOWNLOADS.is_dir() or DOWNLOADS.is_symlink():
        return dict(state='FAILED',error_code='DOWNLOADS_UNAVAILABLE',exit_code=code,snapshot=after)
    stem = re.sub(r'[^A-Za-z0-9_-]+','_',guard.project['name'])[:40].strip('_') or 'AndroidProject'
    target = DOWNLOADS / (stem+'_'+remote[:12]+'_'+jid[:8]+'_debug.apk')
    tmp = DOWNLOADS / ('.animadev-'+jid+'.part')
    digest=hashlib.sha256()
    try:
        fd=os.open(tmp,os.O_CREAT|os.O_EXCL|os.O_WRONLY|os.O_NOFOLLOW,0o600)
        with os.fdopen(fd,'wb') as output, apk.open('rb') as source:
            while True:
                buf=source.read(1024*1024)
                if not buf: break
                digest.update(buf);output.write(buf)
            output.flush();os.fsync(output.fileno())
        if target.exists() or target.is_symlink(): raise OSError()
        os.rename(tmp,target)
    except OSError:
        try: tmp.unlink()
        except OSError: pass
        return dict(state='FAILED',error_code='ARTIFACT_EXPORT_FAILED',exit_code=code,snapshot=after)
    return dict(state='SUCCEEDED',error_code='',exit_code=0,snapshot=after,
                artifact={'kind':'DEBUG_APK','path':str(target),'size':s.st_size,
                          'sha256':digest.hexdigest(),'commit':remote,'branch':checked['branch']})
