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
DENIED={'storage','android-sdk','sdk','bin','lib','node_modules','venv','env','tmp','Downloads','Android','build','dist','target','cache','caches','secrets','credentials','keystores','gradle','__pycache__'}
MAX_REPOS=40
MAX_DEPTH=6
MAX_DIRS=1000
SCAN_SECONDS=20

def permitted(root,home=HOME):
    root=pathlib.Path(root);home=pathlib.Path(home)
    try:
        parts=root.relative_to(home).parts
        return (root.resolve()==root.absolute() and 1<=len(parts)<=MAX_DEPTH and
            not any(p.startswith('.') or p in DENIED for p in parts))
    except (OSError,ValueError):return False

def scan(home=HOME):
    home=pathlib.Path(home);queue=[home];found=[];issues=[];visited=0
    deadline=time.monotonic()+SCAN_SECONDS
    def note(path,reason):
        if len(issues)<40:issues.append(dict(path=str(path),reason=reason))
    while queue:
        if visited>=MAX_DIRS or time.monotonic()>deadline:
            note(home,'Поиск остановлен по лимиту времени/каталогов; используйте точный путь.');break
        parent=queue.pop(0);visited+=1
        try:
            with os.scandir(parent) as it:
                import itertools
                children=sorted(itertools.islice(it,2001),key=lambda x:x.name)
                if len(children)>2000:note(parent,'Лимит содержимого каталога; используйте точный путь.');children=children[:2000]
        except OSError:
            note(parent,'Каталог недоступен');continue
        for entry in children:
            # Never inspect hidden names or descend into protected directories.
            if entry.name.startswith('.') or entry.name in DENIED:continue
            path=pathlib.Path(entry.path)
            if entry.is_symlink():note(path,'Символьная ссылка не обходится');continue
            try:
                if not entry.is_dir(follow_symlinks=False):continue
                depth=len(path.relative_to(home).parts)
                if depth>MAX_DEPTH:note(parent,'Достигнута глубина поиска');continue
                if not permitted(path,home):note(path,'Путь не разрешён');continue
                marker=path/'.git'
                if marker.exists() or marker.is_symlink():found.append(path);continue
                if depth<MAX_DEPTH:queue.append(path)
                else:note(path,'Достигнута глубина поиска')
            except OSError:note(path,'Каталог недоступен')
    return found,issues,visited,bool(queue)

def candidates(home=HOME):return scan(home)[0]

def git_directory(root,home):
    marker=root/'.git'
    if marker.is_symlink() or marker.resolve()!=marker.absolute():return None
    if marker.is_dir():return marker,False,marker
    if not marker.is_file() or marker.stat().st_size>4096:return None
    value=marker.read_text().strip()
    if not value.startswith('gitdir: '):return None
    target=pathlib.Path(value[8:]);target=target if target.is_absolute() else root/target
    # Linked worktrees must point into a normal repository's own .git/worktrees.
    target=target.absolute()
    parts=target.parts
    try:
        idx=parts.index('.git');base=pathlib.Path(*parts[:idx])
        if parts[idx:]!=('.git','worktrees',parts[-1]) or not permitted(base,home):return None
        if target.resolve()!=target or not target.is_dir():return None
        common=base/'.git'
        if not common.is_dir() or common.is_symlink() or common.resolve()!=common:return None
        for name in ('commondir','gitdir','HEAD'):
            file=target/name
            if not file.is_file() or file.is_symlink() or file.stat().st_size>4096:return None
        if (target/(target/'commondir').read_text().strip()).resolve()!=common:return None
        backlink=pathlib.Path((target/'gitdir').read_text().strip())
        if backlink!=marker:return None
        return common,True,target
    except (ValueError,OSError,IndexError):return None

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
    try:location=git_directory(root,pathlib.Path(home))
    except (OSError,UnicodeError,ValueError):return None
    if location is None:return None
    config_root,worktree,actual=location
    executable=str(git or PREFIX/'bin/git')
    env={k:v for k,v in os.environ.items() if not k.startswith('GIT_')}
    env.update(GIT_CONFIG_NOSYSTEM='1',GIT_CONFIG_GLOBAL=os.devnull,GIT_OPTIONAL_LOCKS='0',LC_ALL='C',GIT_TERMINAL_PROMPT='0',GIT_NO_LAZY_FETCH='1',GIT_ATTR_NOSYSTEM='1')
    prefix=[executable,'-c','core.hooksPath='+os.devnull,'-c','core.fsmonitor=false','-c','core.untrackedCache=false','-c','core.attributesFile='+os.devnull,'-c','core.excludesFile='+os.devnull,'-c','core.pager=cat','-c','gc.auto=0','-c','maintenance.auto=false']
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
            try:
                if p.poll() is None:os.killpg(p.pid,signal.SIGKILL)
            except ProcessLookupError:pass
            p.wait();raise
        finally:selector.close();p.stdout.close()
    jid='animaquiz' if root==pathlib.Path(home)/'quizapp' else hashlib.sha256(str(root).encode()).hexdigest()[:32]
    result=dict(id=jid,name='AnimaQuiz' if jid=='animaquiz' else root.name[:80],path=str(root),branch='',commit='',remote='',dirty=None,error='',worktree=worktree)
    try:
        for config in (config_root/'config',marker.parent/'.git/config.worktree'):
            if config.exists() and (config.is_symlink() or config.stat().st_size>65536):raise ValueError('CONFIG_DENIED')
        code,names=call(['config','--local','--no-includes','--name-only','--get-regexp',r'^(filter\.|include\.|includeif\.|core\.(gitproxy|sshcommand|alternaterefscommand)|extensions\.partialclone|remote\..*\.(vcs|uploadpack|promisor)|url\.)'])
        if code not in (0,1) or (code==0 and names):raise ValueError('CONFIG_DENIED')
        if worktree:
            for config in (config_root/'config',actual/'config.worktree'):
                if config.exists():
                    if config.is_symlink() or config.stat().st_size>65536:raise ValueError('CONFIG_DENIED')
                    code,names=call(['config','--file',str(config),'--no-includes','--name-only','--get-regexp',r'^(filter\.|include\.|includeif\.|core\.(gitproxy|sshcommand|alternaterefscommand)|extensions\.partialclone|remote\..*\.(vcs|uploadpack|promisor)|url\.)'])
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
    roots,issues,visited,limited=scan(home);entries=[];deadline=time.monotonic()+SCAN_SECONDS
    for root in roots:
        if time.monotonic()>deadline or len(entries)>=MAX_REPOS:
            limited=True;issues.append(dict(path=str(home),reason='Лимит чтения метаданных; используйте точный путь.'));break
        item=inspect(root,home,git)
        if item:entries.append(item)
        elif len(issues)<40:issues.append(dict(path=str(root),reason='.git или worktree не прошёл проверку безопасности'))
    return dict(projects=entries,issues=issues,visited=visited,limited=limited or bool(issues),depth=MAX_DEPTH,scanned_at=time.time())

def explicit(path,home=HOME,git=None):
    value=str(path)
    if value.startswith('~/'):value=str(home)+value[1:]
    root=pathlib.Path(value)
    if not permitted(root,home):return dict(projects=[],issues=[dict(path='',reason='Путь запрещён: разрешён HOME, глубина до 6, без скрытых/служебных каталогов и ссылок.')])
    item=inspect(root,home,git)
    return dict(projects=[item] if item else [],issues=[] if item else [dict(path=str(root),reason='Безопасный Git-репозиторий или worktree не найден.')])

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
    request,mode=sys.argv[1:3]
    if not re.fullmatch('[a-f0-9]{32}',request) or mode not in ('CATALOG','ENV','PATH') or len(sys.argv)!=(4 if mode=='PATH' else 3):return 1
    try: data=catalog() if mode=='CATALOG' else explicit(sys.argv[3]) if mode=='PATH' and len(sys.argv)==4 else environment()
    except Exception:
        print(json.dumps(dict(request_id=request,mode=mode,ok=False,message='Не удалось прочитать разрешённые каталоги Termux.')));return 0
    print(json.dumps(dict(request_id=request,mode=mode,ok=True,data=data),ensure_ascii=False));return 0

if __name__=='__main__':sys.exit(main())
