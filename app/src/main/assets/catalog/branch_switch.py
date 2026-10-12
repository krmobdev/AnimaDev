"""Explicit, guarded local-branch switch for verified Termux Git projects.

No force, reset, clean, checkout of remote-only refs, merge or push.
Queries GitHub using ls-remote before switching. Fails closed for dirty worktrees.
"""
import json, os, pathlib, re, subprocess, sys
HOME=pathlib.Path("/data/data/com.termux/files/home")
ROOT=HOME/"quizapp"   # Legacy unit-test default; one-shot entrypoint sets validated root.
DENIED={"storage","android-sdk","sdk","bin","lib","node_modules","venv","env",
        "tmp","Downloads","Android","build","dist","target","cache","caches",
        "secrets","credentials","keystores","gradle","__pycache__"}
REMOTE=re.compile(r"github[.]com/[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")
SHA=re.compile(r"[a-f0-9]{40,64}")
NAME=re.compile(r"[A-Za-z0-9][A-Za-z0-9_./-]{0,179}")
GIT="/data/data/com.termux/files/usr/bin/git"

class SwitchDenied(Exception): pass

def valid_branch(name):
    return bool(NAME.fullmatch(name) and not name.endswith(("/",".",".lock")) and
                ".." not in name and "//" not in name and "@{" not in name and
                not name.startswith(".") and not name.endswith("-") and
                all(not item.startswith(".") for item in name.split("/")))

def run(args, timeout=70):
    env={k:v for k,v in os.environ.items() if not k.startswith("GIT_")}
    for name in ("BASH_ENV","ENV","JAVA_TOOL_OPTIONS","JDK_JAVA_OPTIONS","_JAVA_OPTIONS"):env.pop(name,None)
    env.update(GIT_TERMINAL_PROMPT="0",GIT_CONFIG_NOSYSTEM="1",
               GIT_CONFIG_GLOBAL="/dev/null",LC_ALL="C")
    try:
        p=subprocess.run([GIT,"-c","core.hooksPath=/dev/null",
                "-c","credential.helper=","-c","protocol.file.allow=never",
                "-c","core.fsmonitor=false",*args],cwd=ROOT,env=env,
                stdin=subprocess.DEVNULL,stdout=subprocess.PIPE,stderr=subprocess.DEVNULL,
                timeout=timeout,check=False)
    except (OSError,subprocess.TimeoutExpired):
        raise SwitchDenied("GIT_UNAVAILABLE")
    if len(p.stdout)>8192 or p.returncode:raise SwitchDenied("GIT_REJECTED")
    return p.stdout.decode("utf-8","replace").strip()

def validate_project(root_string, expected_remote):
    """Accept only a normal Git checkout under Termux HOME, no worktrees or links."""
    root=pathlib.Path(root_string)
    if not REMOTE.fullmatch(expected_remote):
        raise SwitchDenied("REMOTE_DENIED")
    try:
        parts=root.relative_to(HOME).parts
        if not 1<=len(parts)<=6 or any(not x or x.startswith(".") or x in DENIED for x in parts):
            raise ValueError()
        if root.resolve()!=root.absolute() or not (root/".git").is_dir() or (root/".git").is_symlink():
            raise ValueError()
    except (OSError, ValueError):
        raise SwitchDenied("PROJECT_DENIED")
    if root==HOME/"quizapp" and expected_remote!="github.com/krmobdev/quizapp":
        raise SwitchDenied("REMOTE_DENIED")
    return root

def switch(target,expected_current,expected_target,expected_remote="github.com/krmobdev/quizapp"):
    if not valid_branch(target) or not SHA.fullmatch(expected_current) or not SHA.fullmatch(expected_target):
        raise SwitchDenied("INVALID_INPUT")
    if not ROOT.is_dir() or ROOT.is_symlink() or not (ROOT/".git").is_dir():
        raise SwitchDenied("PROJECT_DENIED")
    if run(["rev-parse","--show-toplevel"])!=str(ROOT):
        raise SwitchDenied("PROJECT_DENIED")
    origin=run(["remote","get-url","origin"])
    normalized=origin.replace("git@github.com:","github.com/").replace("https://","").removesuffix(".git")
    if normalized!=expected_remote or not REMOTE.fullmatch(normalized):
        raise SwitchDenied("REMOTE_DENIED")
    current=run(["symbolic-ref","--quiet","--short","HEAD"])
    if run(["rev-parse","HEAD"])!=expected_current:
        raise SwitchDenied("SOURCE_CHANGED")
    if run(["status","--porcelain=v1","--untracked-files=all"]):
        raise SwitchDenied("WORKTREE_DIRTY")
    if not run(["show-ref","--verify","refs/heads/"+target]) or run(["rev-parse","refs/heads/"+target])!=expected_target:
        raise SwitchDenied("LOCAL_BRANCH_CHANGED")
    raw=run(["ls-remote","--heads","origin","refs/heads/"+target],timeout=60)
    line=raw.splitlines()
    if len(line)!=1 or line[0]!=expected_target+"\trefs/heads/"+target:
        raise SwitchDenied("GITHUB_SHA_MISMATCH")
    if target==current:
        return {"branch":target,"commit":expected_target,"unchanged":True}
    # Refuse branches checked out elsewhere; git switch will enforce it without --ignore-other-worktrees.
    run(["switch","--no-guess",target],timeout=90)
    if run(["symbolic-ref","--quiet","--short","HEAD"])!=target or run(["rev-parse","HEAD"])!=expected_target:
        raise SwitchDenied("SWITCH_RESULT_UNCONFIRMED")
    if run(["status","--porcelain=v1","--untracked-files=all"]):
        raise SwitchDenied("SWITCH_RESULT_UNCONFIRMED")
    return {"branch":target,"commit":expected_target,"unchanged":False}

def main():
    global ROOT
    request=""
    try:
        if len(sys.argv)!=7:raise SwitchDenied("INVALID_INPUT")
        request,project_path,remote,target,current,selected=sys.argv[1:]
        if not re.fullmatch(r"[a-f0-9]{32}",request):raise SwitchDenied("INVALID_INPUT")
        ROOT=validate_project(project_path,remote)
        outcome=switch(target,current,selected,remote)
        print(json.dumps({"request_id":request,"ok":True,**outcome}))
    except SwitchDenied as err:
        print(json.dumps({"request_id":request,"ok":False,"error_code":str(err)}))
    except Exception:
        print(json.dumps({"request_id":request,"ok":False,"error_code":"SWITCH_UNCONFIRMED"}))

if __name__=="__main__":main()
