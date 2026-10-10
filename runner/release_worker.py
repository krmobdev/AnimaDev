"""One-shot Release AAB worker. Secrets arrive ONLY via authenticated hybrid envelope.

This source is concatenated after release_signer.py from the signed Android APK.
No dynamic shell commands, keystore paths, secret environment variables, Git writes,
job database modifications, or password/keystore logging are permitted.
"""
import base64
import fcntl
import hashlib
import json
import os
import pathlib
import re
import subprocess
import sys
import time
import signal

HOME=pathlib.Path("/data/data/com.termux/files/home")
STATE=HOME/".local/share/animadev"
DOWNLOADS=pathlib.Path("/storage/emulated/0/Download")
GIT=str(HOME.parent/"usr/bin/git")
GRADLE_TIMEOUT=5400
MAX_MESSAGE=180000
RX_ID=re.compile(r"^[a-f0-9]{32}$")
RX_REF=re.compile(r"^[A-Za-z0-9_./-]{1,200}$")
RX_REMOTE=re.compile(r"^github[.]com/[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$")
RX_ROOT_DENIED={"storage","android-sdk","sdk","bin","lib","node_modules","venv",
                "env","tmp","Downloads","Android","build","dist","target",
                "cache","caches","secrets","credentials","keystores","gradle","__pycache__"}

class ReleaseFailure(Exception):
    def __init__(self, code):
        super().__init__(code)
        self.code=code

def _valid_project(req):
    if not isinstance(req,dict) or set(req)!={"id","root","remote","branch","name"}:
        raise ReleaseFailure("PROJECT_DENIED")
    if any(type(v) is not str for v in req.values()):
        raise ReleaseFailure("PROJECT_DENIED")
    root=pathlib.Path(req["root"])
    try:
        parts=root.relative_to(HOME).parts
        if not 1<=len(parts)<=6 or any(not p or p.startswith(".") or p in RX_ROOT_DENIED for p in parts):
            raise ValueError()
        if root.resolve()!=root.absolute() or not root.is_dir():raise ValueError()
        marker=root/".git"
        if marker.is_symlink() or not marker.is_dir():raise ValueError()
    except (OSError,ValueError):
        raise ReleaseFailure("PROJECT_DENIED")
    if req["root"]==str(HOME/"quizapp"):
        if req["id"]!="animaquiz" or req["remote"]!="github.com/krmobdev/quizapp" or req["branch"]!="test/decomposition-v1":
            raise ReleaseFailure("PROJECT_DENIED")
    elif req["id"]!=hashlib.sha256(req["root"].encode()).hexdigest()[:32]:
        raise ReleaseFailure("PROJECT_DENIED")
    branch=req["branch"]
    if not RX_REF.fullmatch(branch) or branch.startswith("-") or branch.endswith(("/",".",".lock")) or ".." in branch or "//" in branch:
        raise ReleaseFailure("PROJECT_DENIED")
    if not RX_REMOTE.fullmatch(req["remote"]) or not 1<=len(req["name"])<=80:
        raise ReleaseFailure("PROJECT_DENIED")
    return root

def _git(root, *args, timeout=120):
    env=dict(os.environ)
    for k in list(env):
        if k.startswith("GIT_") or k in ("BASH_ENV","ENV","JAVA_TOOL_OPTIONS","JDK_JAVA_OPTIONS","_JAVA_OPTIONS","GRADLE_OPTS","JAVA_OPTS"):
            env.pop(k,None)
    env.update(GIT_CONFIG_NOSYSTEM="1",GIT_CONFIG_GLOBAL=os.devnull,
               GIT_TERMINAL_PROMPT="0",LC_ALL="C")
    try:
        result=subprocess.run([GIT,"-c","core.hooksPath=/dev/null",
            "-c","credential.helper=","-c","protocol.file.allow=never",*args],
            cwd=root,env=env,stdin=subprocess.DEVNULL,stdout=subprocess.PIPE,
            stderr=subprocess.DEVNULL,timeout=timeout,check=False)
        if result.returncode!=0 or len(result.stdout)>10000:
            raise ReleaseFailure("GIT_VERIFICATION_FAILED")
        return result.stdout.decode("utf-8","replace").strip()
    except (OSError,subprocess.TimeoutExpired):
        raise ReleaseFailure("GIT_VERIFICATION_FAILED")

def _git_clean(root, req, fetch=False):
    if _git(root,"rev-parse","--show-toplevel")!=str(root):
        raise ReleaseFailure("PROJECT_DENIED")
    if _git(root,"symbolic-ref","--short","HEAD")!=req["branch"]:
        raise ReleaseFailure("BRANCH_DENIED")
    origin=_git(root,"remote","get-url","origin")
    normalized=origin.replace("git@github.com:","github.com/").removesuffix(".git")
    if normalized!=req["remote"]:
        raise ReleaseFailure("REMOTE_MISMATCH")
    if _git(root,"status","--porcelain=v1","--untracked-files=all"):
        raise ReleaseFailure("REPO_DIRTY")
    if fetch:
        _git(root,"fetch","--no-tags","origin",req["branch"],timeout=120)
    sha=_git(root,"rev-parse","HEAD")
    remote_sha=_git(root,"rev-parse","refs/remotes/origin/"+req["branch"])
    if not re.fullmatch(r"[a-f0-9]{40,64}",sha) or sha!=remote_sha:
        raise ReleaseFailure("BUILD_OUTDATED")
    return sha

def _bundle_file(root):
    target=root/"app/build/outputs/bundle/release/app-release.aab"
    if target.is_symlink() or target.parent.resolve()!=target.parent.absolute():
        raise ReleaseFailure("ARTIFACT_PATH_DENIED")
    return target

def _export(signed,request,project,sha,meta):
    if not DOWNLOADS.is_dir() or DOWNLOADS.is_symlink():
        raise ReleaseFailure("DOWNLOADS_UNAVAILABLE")
    name=re.sub(r"[^A-Za-z0-9_-]+","_",project["name"])[:35].strip("_") or "AndroidProject"
    target=DOWNLOADS/(name+"_"+sha[:12]+"_"+request[:8]+"_release.aab")
    tmp=DOWNLOADS/(".animadev-release-"+request+".part")
    if target.exists() or target.is_symlink() or tmp.exists() or tmp.is_symlink():
        raise ReleaseFailure("ARTIFACT_PATH_DENIED")
    digest=hashlib.sha256()
    try:
        fd=os.open(tmp,os.O_WRONLY|os.O_CREAT|os.O_EXCL|os.O_NOFOLLOW,0o600)
        with os.fdopen(fd,"wb") as output,signed.open("rb") as source:
            while True:
                data=source.read(1024*1024)
                if not data:break
                output.write(data)
                digest.update(data)
            output.flush()
            os.fsync(output.fileno())
        if digest.hexdigest()!=meta["sha256"]:
            raise ReleaseFailure("SIGNATURE_INVALID")
        os.rename(tmp,target)
    except (OSError,ReleaseFailure):
        try:tmp.unlink()
        except OSError:pass
        raise ReleaseFailure("EXPORT_FAILED")
    return {"kind":"RELEASE_AAB","path":str(target),"sha256":digest.hexdigest(),
            "size":target.stat().st_size,"commit":sha}

def run(payload):
    required={"request_id","project","alias","keystore","store_password","key_password"}
    if type(payload) is not dict or set(payload)!=required or not isinstance(payload["request_id"],str) or not RX_ID.fullmatch(payload["request_id"]):
        raise ReleaseFailure("RESULT_MALFORMED")
    project=payload["project"]
    request=payload["request_id"]
    alias=payload["alias"]
    root=_valid_project(project)
    if type(payload["alias"]) is not str or not ALIAS.fullmatch(payload["alias"]):
        raise ReleaseFailure("SIGNING_INPUT_DENIED")
    if any(type(payload[k]) is not str for k in ("keystore","store_password","key_password")):
        raise ReleaseFailure("SIGNING_INPUT_DENIED")
    if len(payload["keystore"])>4*MAX_KEY_BYTES//3+8:
        raise ReleaseFailure("SIGNING_INPUT_DENIED")
    try:
        key_bytes=bytearray(base64.b64decode(payload["keystore"],validate=True))
    except (ValueError,base64.binascii.Error):
        raise ReleaseFailure("SIGNING_INPUT_DENIED")
    store_password=bytearray(payload["store_password"].encode("utf-8"))
    key_password=bytearray((payload["key_password"] or payload["store_password"]).encode("utf-8"))
    payload.clear() # no further references to input secret values in this function
    try:
        if not 0<len(key_bytes)<=MAX_KEY_BYTES or not 0<len(store_password)<=MAX_PASSWORD or not 0<len(key_password)<=MAX_PASSWORD:
            raise ReleaseFailure("SIGNING_INPUT_DENIED")
        os.umask(0o077)
        lock=STATE/("project-"+project["id"]+".lock")
        if not STATE.is_dir() or STATE.is_symlink() or lock.is_symlink():
            raise ReleaseFailure("STATE_UNAVAILABLE")
        fd=os.open(lock,os.O_CREAT|os.O_RDWR|os.O_NOFOLLOW,0o600)
        global_fd=None
        try:
            # Shared with animadev_runner.py: only ONE Gradle job on the phone.
            global_lock=STATE/".build-global.lock"
            if global_lock.is_symlink():raise ReleaseFailure("STATE_UNAVAILABLE")
            global_fd=os.open(global_lock,os.O_CREAT|os.O_RDWR|os.O_NOFOLLOW,0o600)
            try:fcntl.flock(global_fd,fcntl.LOCK_EX|fcntl.LOCK_NB)
            except BlockingIOError:raise ReleaseFailure("JOB_BUSY")
            try: fcntl.flock(fd,fcntl.LOCK_EX|fcntl.LOCK_NB)
            except BlockingIOError:raise ReleaseFailure("JOB_BUSY")
            active_path=STATE/(".release-active-"+request)
            cancel_path=STATE/(".release-cancel-"+request)
            if active_path.is_symlink() or cancel_path.is_symlink():
                raise ReleaseFailure("STATE_UNAVAILABLE")
            if active_path.exists() or cancel_path.exists():
                raise ReleaseFailure("STATE_UNAVAILABLE")
            marker_fd=os.open(active_path,os.O_CREAT|os.O_EXCL|os.O_WRONLY|os.O_NOFOLLOW,0o600)
            os.close(marker_fd)
            sha=_git_clean(root,project,fetch=True)
            gradlew=root/"gradlew"
            if not gradlew.is_file() or gradlew.is_symlink():
                raise ReleaseFailure("GRADLE_MISSING")
            unsigned=_bundle_file(root)
            old=None
            if unsigned.exists():
                stat=unsigned.stat()
                old=(stat.st_ino,stat.st_size,stat.st_mtime_ns,stat.st_ctime_ns)
            # The entire Gradle process has no knowledge of credentials.
            env=dict(os.environ)
            for key in ("JAVA_TOOL_OPTIONS","JDK_JAVA_OPTIONS","_JAVA_OPTIONS","GRADLE_OPTS","JAVA_OPTS"):
                env.pop(key,None)
            if cancel_path.is_file():raise ReleaseFailure("CANCELLED")
            try:
                proc=subprocess.Popen(["/data/data/com.termux/files/usr/bin/bash",
                    "./gradlew",":app:bundleRelease","--offline","--no-daemon","--rerun-tasks","--console=plain"],
                    cwd=root,env=env,stdin=subprocess.DEVNULL,stdout=subprocess.DEVNULL,
                    stderr=subprocess.DEVNULL,start_new_session=True)
            except OSError:
                raise ReleaseFailure("GRADLE_MISSING")
            interrupted=False
            sigint_at=None
            deadline=time.monotonic()+GRADLE_TIMEOUT
            try:
                while proc.poll() is None:
                    if cancel_path.is_file() and not interrupted:
                        try:os.killpg(proc.pid,signal.SIGINT)
                        except ProcessLookupError:pass
                        interrupted=True;sigint_at=time.monotonic()
                    if interrupted and sigint_at is not None and time.monotonic()-sigint_at>35:
                        # A stuck Gradle daemon cannot hold the exclusive build slot forever.
                        try:os.killpg(proc.pid,signal.SIGKILL)
                        except ProcessLookupError:pass
                    if time.monotonic()>=deadline:
                        try:os.killpg(proc.pid,signal.SIGKILL)
                        except ProcessLookupError:pass
                        proc.wait()
                        raise ReleaseFailure("BUILD_TIMEOUT_UNKNOWN")
                    time.sleep(.3)
                proc.wait()
            finally:
                if proc.poll() is None:
                    try:os.killpg(proc.pid,signal.SIGKILL)
                    except ProcessLookupError:pass
                    proc.wait()
            if interrupted or cancel_path.is_file():
                raise ReleaseFailure("CANCELLED")
            if proc.returncode!=0:
                raise ReleaseFailure("GRADLE_FAILED")
            if _git_clean(root,project)!=sha:
                raise ReleaseFailure("BUILD_OUTDATED")
            if not unsigned.is_file() or unsigned.is_symlink():
                raise ReleaseFailure("ARTIFACT_INVALID")
            stat=unsigned.stat()
            if old==(stat.st_ino,stat.st_size,stat.st_mtime_ns,stat.st_ctime_ns):
                raise ReleaseFailure("ARTIFACT_INVALID")
            signed=unsigned.parent/(".animadev-"+project["id"]+"-"+sha[:12]+"-"+os.urandom(4).hex()+".aab")
            try:
                if cancel_path.is_file():raise ReleaseFailure("CANCELLED")
                meta=sign_bundle(unsigned,signed,key_bytes,store_password,key_password,alias,
                                 jarsigner="/data/data/com.termux/files/usr/bin/jarsigner")
                if _git_clean(root,project)!=sha:
                    raise ReleaseFailure("BUILD_OUTDATED")
                if cancel_path.is_file():raise ReleaseFailure("CANCELLED")
                artifact=_export(signed,request,project,sha,meta)
            finally:
                try:signed.unlink()
                except OSError:pass
            return artifact
        finally:
            # Cancellation markers never contain a signing secret.
            # Delete them only if this request created the active marker.
            if 'active_path' in locals() and active_path.exists():
                try:active_path.unlink()
                except OSError:pass
                if 'cancel_path' in locals():
                    try:cancel_path.unlink()
                    except OSError:pass
            os.close(fd)
            if global_fd is not None:os.close(global_fd)
    finally:
        key_bytes[:]=b"\0"*len(key_bytes)
        store_password[:]=b"\0"*len(store_password)
        key_password[:]=b"\0"*len(key_password)

def _receive_secure_payload():
    # Termux has no permission to connect to an unrelated Android app's
    # Unix socket on many devices. Only an RSA-OAEP/AES-GCM envelope enters
    # the RUN_COMMAND_STDIN extra; the one-time RSA private key stays in Termux.
    if len(sys.argv)!=3 or not RX_ID.fullmatch(sys.argv[1]) or not re.fullmatch(r"[a-f0-9]{64}",sys.argv[2]):
        raise ReleaseFailure("CHANNEL_DENIED")
    request,expected_sha=sys.argv[1:]
    source=STATE/"ReleaseTransport.java"
    try:
        if source.is_symlink() or not source.is_file() or hashlib.sha256(source.read_bytes()).hexdigest()!=expected_sha:
            raise ReleaseFailure("CHANNEL_DENIED")
        ciphertext=sys.stdin.buffer.read(230001)
        if not 500<=len(ciphertext)<=230000:
            raise ReleaseFailure("ENVELOPE_MISSING")
        result=subprocess.run(["java",str(source),"decrypt",request],input=ciphertext,
            stdout=subprocess.PIPE,stderr=subprocess.DEVNULL,timeout=90,check=False)
        if result.returncode!=0 or not 50<=len(result.stdout)<=MAX_MESSAGE:
            raise ReleaseFailure("TRANSPORT_DECRYPT_FAILED")
        payload=json.loads(result.stdout)
        if type(payload) is not dict or payload.get("request_id")!=request:
            raise ReleaseFailure("CHANNEL_DENIED")
        return payload
    except (OSError,subprocess.TimeoutExpired,ValueError,UnicodeError,json.JSONDecodeError):
        raise ReleaseFailure("TRANSPORT_UNAVAILABLE")

def main():
    os.umask(0o077)
    request_id=""
    try:
        if len(sys.argv)>=2 and RX_ID.fullmatch(sys.argv[1]):
            request_id=sys.argv[1]
        payload=_receive_secure_payload()
        request_id=payload.get("request_id","") if isinstance(payload,dict) else ""
        if not isinstance(request_id,str) or not RX_ID.fullmatch(request_id):
            raise ReleaseFailure("RESULT_MALFORMED")
        artifact=run(payload)
        print(json.dumps({"version":1,"request_id":request_id,"ok":True,
                          "artifact":artifact},ensure_ascii=False))
    except (ReleaseFailure,SigningError) as exc:
        print(json.dumps({"version":1,"request_id":request_id,"ok":False,
                          "error_code":exc.code},ensure_ascii=False))
    except Exception:
        print(json.dumps({"version":1,"request_id":request_id,"ok":False,
                          "error_code":"RESULT_UNCONFIRMED"},ensure_ascii=False))

if __name__=="__main__":
    main()
