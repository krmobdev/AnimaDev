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
import tempfile
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
 'ADB_NOT_READY': 'Не найдено ровно одно авторизованное физическое устройство в adb devices -l. Сопряжение без adb connect недостаточно.',
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

def summary(before, after, redact=lambda text:text):
    fresh = [data for name,(stamp,data) in after.items() if name not in before or before[name][0] != stamp]
    if not fresh: raise ReportError('REPORTS_MISSING')
    total = failed = skipped = 0
    duration = 0.0
    failure_details = []
    for data in fresh:
        if b'<!DOCTYPE' in data.upper() or b'<!ENTITY' in data.upper(): raise ReportError('REPORTS_INVALID')
        try:
            document = ET.fromstring(data)
            if document.tag == 'testsuite': suites = [document]
            elif document.tag == 'testsuites': suites = list(document)
            else: raise ValueError()
            if not suites or any(suite.tag != 'testsuite' for suite in suites): raise ValueError()
            rt = rf = re_count = rs = 0
            for suite in suites:
                cases = suite.findall('testcase')
                n = int(suite.attrib['tests']); f = int(suite.attrib.get('failures','0'))
                e = int(suite.attrib.get('errors','0')); sk = int(suite.attrib.get('skipped','0'))
                d = float(suite.attrib['time'])
                case_fail = sum(case.find('failure') is not None or case.find('error') is not None for case in cases)
                case_skip = sum(case.find('skipped') is not None for case in cases)
                if n != len(cases) or case_fail != f+e or case_skip != sk or case_fail+case_skip > n or min(n,f,e,sk,d)<0 or not math.isfinite(d): raise ValueError()
                for case in cases:
                    failure = case.find('failure')
                    if failure is None: failure = case.find('error')
                    if failure is not None and len(failure_details)<10:
                        safe=SafeLines(redact).feed(failure.get('message') or failure.text or 'Причина не указана')
                        failure_details.append(dict(
                            class_name=redact(case.get('classname') or suite.get('name') or '')[:160],
                            test_name=redact(case.get('name') or '')[:160],
                            message=safe[:400]))
                rt += n; rf += f; re_count += e; rs += sk; duration += d
            if document.tag == 'testsuites':
                for key,value in (('tests',rt),('failures',rf),('errors',re_count),('skipped',rs)):
                    if key in document.attrib and int(document.attrib[key]) != value: raise ValueError()
            total += rt; failed += rf+re_count; skipped += rs
        except (ET.ParseError, KeyError, ValueError, OverflowError): raise ReportError('REPORTS_INVALID')
    result={'total':total,'passed':total-failed-skipped,'failed':failed,'skipped':skipped,
            'duration_ms':round(duration*1000),'report_files':len(fresh)}
    if failure_details:result['failure_details']=failure_details
    return result

def result_message(action,code):
    if code in ('TESTS_FAILED','TEST_FAILED') and action=='TEST_DEVICE':
        return 'Есть проваленные инструментальные тесты. Откройте журнал: там имена тестов и причины.'
    return MESSAGES.get(code)


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
    try:result['tests']=summary(before,reports(guard.root),redact=redact)
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

def execute_debug(guard, log, on_process, identity, redact, jid, timeout=TIMEOUT, output_dir=str(DOWNLOADS)):
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
    if (not isinstance(output_dir, str) or not output_dir.startswith('/storage/emulated/0/')
            or len(output_dir)>520):
        return dict(state='FAILED',error_code='DOWNLOADS_UNAVAILABLE',exit_code=code,snapshot=after)
    parts=output_dir[len('/storage/emulated/0/'):].split('/')
    if not all(re.fullmatch(r'[\w ().&-]{1,80}',item) and item not in ('.','..') for item in parts):
        return dict(state='FAILED',error_code='DOWNLOADS_UNAVAILABLE',exit_code=code,snapshot=after)
    folder=pathlib.Path(output_dir)
    if not folder.is_dir() or folder.is_symlink() or folder.resolve()!=folder.absolute():
        return dict(state='FAILED',error_code='DOWNLOADS_UNAVAILABLE',exit_code=code,snapshot=after)
    stem = re.sub(r'[^A-Za-z0-9_-]+','_',guard.project['name'])[:40].strip('_') or 'AndroidProject'
    target = folder / (stem+'_'+remote[:12]+'_'+jid[:8]+'_debug.apk')
    tmp = folder / ('.animadev-'+jid+'.part')
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


# Device instrumentation stays in Termux: never invokes GitHub Actions or CI.
DEVICE_COMMAND = ('./gradlew', ':app:connectedDebugAndroidTest', '--offline',
                  '--no-daemon', '--rerun-tasks', '--console=plain')
DEVICE_RESULTS = 'app/build/outputs/androidTest-results/connected'

def device_init_script(jid):
    if not re.fullmatch(r'[a-f0-9]{32}',jid):raise ValueError('JOB_ID_INVALID')
    suffix='.animadevtests.j'+jid
    return """gradle.beforeProject { p ->
    if (p.path == ':app') {
        p.pluginManager.withPlugin('com.android.application') {
            p.layout.buildDirectory.set(p.layout.projectDirectory.dir('build/animadev-device-j%s'))
            p.extensions.getByName('androidComponents').finalizeDsl { android ->
                def originalId = android.defaultConfig.applicationId
                if (originalId == 'com.animacode.quizapp') {
                    // Keep the known test-only package identity: update in place,
                    // avoiding collisions with its literal test provider authority.
                    android.defaultConfig.testApplicationId = 'com.animacode.quizapp.debug.test'
                    // OS backup seed/restore/verify is a separate multi-process audit.
                    android.defaultConfig.testInstrumentationRunnerArguments['notClass'] =
                        'com.animacode.quizapp.data.NativeBackupPhaseInstrumentedTest'
                }
                android.defaultConfig.applicationId = originalId + '%s'
            }
        }
    }
}
""" % (jid,suffix)

def adb_devices(run=subprocess.run, adb='/data/data/com.termux/files/usr/bin/adb'):
    """Only one authorized hardware/USB/WiFi ADB device is accepted."""
    try:
        result=run([adb,'devices','-l'],stdin=subprocess.DEVNULL,
                   stdout=subprocess.PIPE,stderr=subprocess.DEVNULL,
                   timeout=20,check=False)
        if result.returncode != 0 or len(result.stdout)>16000:return []
        found=[]
        for line in result.stdout.decode('utf-8','replace').splitlines()[1:]:
            fields=line.split()
            if len(fields)>=2 and fields[1]=='device' and re.fullmatch(r'[A-Za-z0-9._:-]{4,128}',fields[0]):
                # adb's virtual emulators are not real connected Android devices.
                if not fields[0].startswith('emulator-'):found.append(fields[0])
        return found[:4]
    except (OSError,subprocess.TimeoutExpired):
        return []

def valid_wifi_endpoint(endpoint):
    """Only explicit RFC1918 IPv4:port; no guessing, pairing or port scans."""
    if type(endpoint) is not str or len(endpoint)>22:
        return False
    match=re.fullmatch(r'([0-9]{1,3}(?:[.][0-9]{1,3}){3}):([0-9]{4,5})',endpoint)
    if not match:return False
    parts=[int(piece) for piece in match.group(1).split('.')]
    port=int(match.group(2))
    return 1024<=port<=65535 and all(n<=255 for n in parts) and (
        parts[0]==10 or parts[0]==172 and 16<=parts[1]<=31 or
        parts[:2]==[192,168])


def connect_known_adb_endpoint(endpoint, run=subprocess.run,
                               adb='/data/data/com.termux/files/usr/bin/adb'):
    """Reconnect a confirmed prior Wi-Fi endpoint; never reset any other device."""
    if not valid_wifi_endpoint(endpoint):return False
    try:
        result=run([adb,'connect',endpoint],stdin=subprocess.DEVNULL,
                   stdout=subprocess.PIPE,stderr=subprocess.DEVNULL,
                   timeout=12,check=False)
        # Successful subprocess return alone does not establish authorization.
        # Caller must recheck adb devices -l.
        return result.returncode==0
    except (OSError,subprocess.TimeoutExpired):
        return False


def reconnect_offline_adb(run=subprocess.run, adb='/data/data/com.termux/files/usr/bin/adb'):
    """Reconnect one stale LAN transport, without disconnecting anyone else."""
    try:
        result=run([adb,'devices','-l'],stdin=subprocess.DEVNULL,
                   stdout=subprocess.PIPE,stderr=subprocess.DEVNULL,
                   timeout=10,check=False)
        if result.returncode or len(result.stdout)>16000:return False
        entries=[]
        for line in result.stdout.decode('utf-8','replace').splitlines()[1:]:
            fields=line.split()
            if len(fields)>=2 and not fields[0].startswith('emulator-'):
                if not re.fullmatch(r'[A-Za-z0-9._:-]{4,128}',fields[0]):
                    return False
                entries.append((fields[0],fields[1]))
        if len(entries)!=1 or entries[0][1]!='offline':return False
        return connect_known_adb_endpoint(entries[0][0],run=run,adb=adb)
    except (OSError,ValueError,subprocess.TimeoutExpired):
        return False

def device_reports(root,jid=''):
    if jid and not re.fullmatch(r'[a-f0-9]{32}',jid):raise ReportError('REPORTS_INVALID')
    folder=pathlib.Path(root)/(('app/build/animadev-device-j'+jid+'/outputs/androidTest-results/connected') if jid else DEVICE_RESULTS)
    if folder.is_symlink() or folder.resolve()!=folder.absolute():raise ReportError('REPORTS_INVALID')
    if not folder.is_dir():return {}
    result={}; total_bytes=0
    # Recursion is bounded by the number and size of XML results.
    for p in folder.rglob('TEST-*.xml'):
        if len(result)>=MAX_FILES or p.is_symlink() or not p.is_file() or p.resolve()!=p.absolute():
            raise ReportError('REPORTS_INVALID')
        data=p.read_bytes()
        if len(data)>MAX_XML:raise ReportError('REPORTS_INVALID')
        total_bytes+=len(data)
        if total_bytes>8*1024*1024:raise ReportError('REPORTS_INVALID')
        st=p.stat()
        result[str(p.relative_to(folder))]=((st.st_ino,st.st_mtime_ns,st.st_ctime_ns,hashlib.sha256(data).hexdigest()),data)
    return result

def execute_device(guard,log,on_process,identity,redact,jid='',timeout=TIMEOUT,adb_endpoint=''):
    snapshot=guard.snapshot()
    guard.require(snapshot,clean=True)
    if guard.project.get('dynamic'):
        if not jid: return dict(state='FAILED',error_code='TEST_STATE_CHANGED',exit_code=None,snapshot=snapshot,tests=None)
        remote=guard.fetch(jid)
        checked=guard.snapshot()
        guard.require(checked,clean=True)
        if snapshot['branch']!=checked['branch'] or snapshot['local_commit']!=checked['local_commit']:
            return dict(state='FAILED',error_code='TEST_STATE_CHANGED',exit_code=None,snapshot=checked,tests=None)
        snapshot=guard.compare(checked,remote)
        if snapshot['local_commit']!=remote or snapshot['ahead'] or snapshot['behind']:
            return dict(state='FAILED',error_code='BUILD_OUTDATED',exit_code=None,snapshot=snapshot,tests=None)
    devices=adb_devices()
    if not devices and adb_endpoint and connect_known_adb_endpoint(adb_endpoint):
        devices=adb_devices()
        log('event','ADB: проверено повторное подключение по сохранённому адресу')
    if not devices and reconnect_offline_adb():
        devices=adb_devices()
        log('event','ADB: попытка восстановления единственного offline Wi-Fi устройства')
    if len(devices)!=1:
        log('event','ADB: ожидается одно подключённое авторизованное устройство; сейчас '+str(len(devices)))
        return dict(state='FAILED',error_code='ADB_NOT_READY',exit_code=None,snapshot=snapshot,tests=None)
    wrapper=guard.root/'gradlew'
    if not wrapper.is_file() or wrapper.is_symlink():
        return dict(state='FAILED',error_code='GRADLE_MISSING',exit_code=None,snapshot=snapshot,tests=None)
    try:before=device_reports(guard.root,jid)
    except ReportError as e:
        return dict(state='FAILED',error_code=e.code,exit_code=None,snapshot=snapshot,tests=None)
    env=dict(guard.env)
    for key in ('JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','_JAVA_OPTIONS','GRADLE_OPTS','JAVA_OPTS'):
        env.pop(key,None)
    env['ANDROID_SERIAL']=devices[0]
    log('event','ADB: одно устройство, серийный номер '+devices[0][:24])
    log('command','bash ./gradlew :app:connectedDebugAndroidTest --offline --no-daemon --rerun-tasks --console=plain')
    # A fresh target package protects installed app databases (e.g. Room 31 -> 30).
    # This fixed init script only changes :app applicationId; source files stay untouched.
    if not re.fullmatch(r'[a-f0-9]{32}',jid):
        return dict(state='FAILED',error_code='TEST_STATE_CHANGED',exit_code=None,snapshot=snapshot,tests=None)
    with tempfile.TemporaryDirectory(prefix='animadev-device-') as folder:
        init=pathlib.Path(folder)/'isolate.gradle'
        init.write_text(device_init_script(jid),encoding='utf-8')
        os.chmod(init,0o600)
        command=DEVICE_COMMAND+('--init-script',str(init))
        log('event','Изолированный тестовый пакет · установленное приложение и его база не изменяются')
        log('event','Профиль: обычные инструментальные тесты. Для com.animacode.quizapp сценарий NativeBackupPhaseInstrumentedTest не выполняется: OS backup/restore требует отдельной многофазной проверки.')
        log('command','bash '+ ' '.join(command))
        code,unknown=process(guard.root,env,log,on_process,identity,redact,
                             timeout,getattr(guard,'observe',lambda:None),command)
    if unknown:
        return dict(state='UNKNOWN',error_code='TEST_TIMEOUT_UNKNOWN',exit_code=None,snapshot=snapshot,tests=None,unresolved=True)
    result=dict(state='FAILED',error_code='GRADLE_FAILED',exit_code=code,snapshot=snapshot,tests=None)
    try:result['tests']=summary(before,device_reports(guard.root,jid),redact=redact)
    except ReportError as e:
        if code==0:result['error_code']=e.code
    # The target must remain exactly the same throughout installation and test run.
    if adb_devices()!=devices:
        return dict(state='FAILED',error_code='ADB_NOT_READY',exit_code=code,
                    snapshot=snapshot,tests=None)
    after=guard.snapshot()
    guard.require(after,clean=True)
    result['snapshot']=after
    if after['branch']!=snapshot['branch'] or after['local_commit']!=snapshot['local_commit']:
        result['error_code']='TEST_STATE_CHANGED'
    elif result['tests'] and result['tests']['failed']:
        result['error_code']='TESTS_FAILED'
    elif code==0 and result['tests'] is not None:
        result.update(state='SUCCEEDED',error_code='')
    return result

