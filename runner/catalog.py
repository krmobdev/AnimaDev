"""Beta read-only catalog/environment probe. Fixed roots and commands; no runner START."""
import hashlib
import json
import os
import pathlib
import re
import selectors
import signal
import subprocess
import sys
import time

HOME=pathlib.Path('/data/data/com.termux/files/home')
PREFIX=HOME.parent/'usr'
DENIED={'storage','android-sdk','sdk','bin','lib','node_modules','venv','env','tmp','Downloads','Android'}
MAX_REPOS=40

def permitted(root,home=HOME):
    root=pathlib.Path(root);home=pathlib.Path(home)
    try:
        relative=root.relative_to(home)
        parts=relative.parts
        return (root.resolve()==root.absolute() and 1<=len(parts)<=2 and
            not any(p.startswith('.') or p in DENIED for p in parts) and
            (len(parts)==1 or parts[0]=='anima-workspace'))
    except (OSError,ValueError):return False

def candidates(home=HOME):
    home=pathlib.Path(home)
    found=[]
    for p in sorted(home.iterdir()):
        if permitted(p,home) and p.is_dir():found.append(p)
    workspace=home/'anima-workspace'
    if workspace.is_dir() and permitted(workspace,home):
        for p in sorted(workspace.iterdir()):
            if permitted(p,home) and p.is_dir():found.append(p)
    return found[:120]

def remote_label(value):
    # Never display userinfo, tokens or nonstandard values. Do not store raw remote.
    value=value.strip()
    match=re.fullmatch(r'git@github\.com:([A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+?)(?:\.git)?',value)
    if match:return 'github.com/'+match.group(1)
    match=re.fullmatch(r'https://github\.com/([A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+?)(?:\.git)?/?',value)
    return 'github.com/'+match.group(1) if match else 'Remote скрыт: нестандартный адрес или данные авторизации'

def inspect(root,home=HOME,git=None):
    root=pathlib.Path(root)
    if not permitted(root,home):return None
    marker=root/'.git'
    if not marker.is_dir() or marker.is_symlink() or marker.resolve()!=marker.absolute():return None
    executable=str(git or PREFIX/'bin/git')
    env={k:v for k,v in os.environ.items() if not k.startswith('GIT_')}
    env.update(GIT_CONFIG_NOSYSTEM='1',GIT_CONFIG_GLOBAL=os.devnull,GIT_OPTIONAL_LOCKS='0',LC_ALL='C',GIT_TERMINAL_PROMPT='0')
    prefix=[executable,'-c','core.hooksPath='+os.devnull,'-c','core.fsmonitor=false','-c','core.untrackedCache=false','-c','core.pager=cat','-c','gc.auto=0','-c','maintenance.auto=false']
    def call(args):
        p=subprocess.Popen(prefix+args,cwd=root,env=env,stdout=subprocess.PIPE,stderr=subprocess.DEVNULL,start_new_session=True)
        selector=selectors.DefaultSelector();selector.register(p.stdout,selectors.EVENT_READ)
        deadline=time.monotonic()+2;output=bytearray()
        try:
            while selector.get_map():
                if time.monotonic()>deadline:raise ValueError('READ_TIMEOUT')
                for key,_ in selector.select(.1):
                    chunk=os.read(key.fileobj.fileno(),4096)
                    if not chunk:selector.unregister(key.fileobj)
                    else:
                        output.extend(chunk)
                        if len(output)>65536:raise ValueError('OUTPUT_LIMIT')
            code=p.wait(timeout=max(.1,deadline-time.monotonic()))
            return code,bytes(output).decode('utf-8','replace').strip()
        except BaseException:
            try:os.killpg(p.pid,signal.SIGKILL)
            except ProcessLookupError:pass
            p.wait();raise
        finally:selector.close();p.stdout.close()
    jid='animaquiz' if root==pathlib.Path(home)/'quizapp' else hashlib.sha256(str(root).encode()).hexdigest()[:32]
    result=dict(id=jid,name='AnimaQuiz' if jid=='animaquiz' else root.name[:80],path=str(root),branch='',commit='',remote='',dirty=None,error='')
    try:
        code,names=call(['config','--local','--no-includes','--name-only','--get-regexp',r'^(filter\.|include\.|includeif\.|core\.gitproxy|remote\..*\.(vcs|uploadpack)|url\.)'])
        if code not in (0,1) or (code==0 and names):raise ValueError('CONFIG_DENIED')
        code,top=call(['rev-parse','--show-toplevel'])
        if code or pathlib.Path(top)!=root:raise ValueError('PATH_DENIED')
        code,branch=call(['symbolic-ref','--quiet','--short','HEAD'])
        if code not in (0,1):raise ValueError('GIT_FAILED')
        result['branch']=branch[:200] if code==0 else 'Detached HEAD'
        code,sha=call(['rev-parse','--verify','HEAD^{commit}'])
        result['commit']=sha if code==0 and re.fullmatch(r'[0-9a-f]{40,64}',sha) else ''
        code,url=call(['config','--local','--no-includes','--get','remote.origin.url'])
        result['remote']=remote_label(url)[:200] if code==0 else 'Origin не настроен'
        code,changes=call(['status','--porcelain=v1','-z','--untracked-files=all'])
        if code:raise ValueError('GIT_FAILED')
        result['dirty']=bool(changes)
    except (OSError,subprocess.TimeoutExpired,ValueError):result['error']='Метаданные недоступны или конфигурация требует ручной проверки'
    return result

def catalog(home=HOME,git=None):
    entries=[];deadline=time.monotonic()+12
    for root in candidates(home):
        if time.monotonic()>deadline:break
        item=inspect(root,home,git)
        if item:entries.append(item)
        if len(entries)>=MAX_REPOS:break
    return dict(projects=entries,limited=len(entries)>=MAX_REPOS or time.monotonic()>deadline,scanned_at=time.time())

def environment():
    def version(path,args):
        if not path.is_file():return 'Не найдено'
        try:
            p=subprocess.run([str(path),*args],stdout=subprocess.PIPE,stderr=subprocess.STDOUT,timeout=3,env={k:v for k,v in os.environ.items() if k not in ('JAVA_TOOL_OPTIONS','_JAVA_OPTIONS','JDK_JAVA_OPTIONS')})
            text=p.stdout[:1024].decode('utf-8','replace').splitlines()
            return text[0][:160] if text else 'Версия не получена'
        except (OSError,subprocess.TimeoutExpired):return 'Недоступно'
    sdk=HOME/'android-sdk';platforms=[]
    if sdk.is_dir() and sdk.resolve()==sdk.absolute():platforms=sorted(p.name for p in (sdk/'platforms').glob('android-*') if p.is_dir() and not p.is_symlink())
    # Available cached distributions, without executing any repository's wrapper.
    gradle=sorted(set(p.name for p in (HOME/'.gradle/wrapper/dists').glob('gradle-*') if p.is_dir() and not p.is_symlink()))
    return dict(python=sys.version.split()[0],git=version(PREFIX/'bin/git',['--version']),java=version(PREFIX/'bin/java',['-version']),sdk=', '.join(platforms) or 'Не найдено',gradle=', '.join(gradle) or 'Нет сохранённых дистрибутивов',platform=sys.platform,architecture=os.uname().machine,checked_at=time.time())

def main():
    request,mode=sys.argv[1:]
    if not re.fullmatch('[a-f0-9]{32}',request) or mode not in ('CATALOG','ENV'):return 1
    try: data=catalog() if mode=='CATALOG' else environment()
    except Exception:
        print(json.dumps(dict(request_id=request,mode=mode,ok=False,message='Не удалось прочитать разрешённые каталоги Termux.')));return 0
    print(json.dumps(dict(request_id=request,mode=mode,ok=True,data=data),ensure_ascii=False));return 0

if __name__=='__main__':sys.exit(main())
