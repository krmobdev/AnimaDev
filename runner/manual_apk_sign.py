"""One-shot standalone APK signer for AnimaDev. Concatenated after release_signer.py.

The Android app stages an explicitly chosen APK in Downloads. Credentials arrive
ONLY over the existing one-time RSA/AES-GCM ReleaseTransport envelope. No Git,
Gradle, keystore files, paths supplied by callers, or command arguments with secrets.
"""
import base64
import fcntl
import hashlib
import json
import os
import pathlib
import re
import shutil
import subprocess
import sys
import tempfile
import zipfile

HOME=pathlib.Path("/data/data/com.termux/files/home")
STATE=HOME/".local/share/animadev"
DOWNLOADS=pathlib.Path("/storage/emulated/0/Download")
RX=re.compile(r"^[a-f0-9]{32}$")
SHA=re.compile(r"^[a-f0-9]{64}$")
MAX_APK=512*1024*1024

class ManualSignFailure(Exception):
    def __init__(self,code):
        super().__init__(code)
        self.code=code

def _receive():
    if len(sys.argv)!=3 or not RX.fullmatch(sys.argv[1]) or not SHA.fullmatch(sys.argv[2]):
        raise ManualSignFailure("CHANNEL_DENIED")
    request,expected=sys.argv[1:]
    java=STATE/"ReleaseTransport.java"
    if java.is_symlink() or not java.is_file() or hashlib.sha256(java.read_bytes()).hexdigest()!=expected:
        raise ManualSignFailure("CHANNEL_DENIED")
    data=sys.stdin.buffer.read(230001)
    if not 500<=len(data)<=230000:
        raise ManualSignFailure("ENVELOPE_MISSING")
    try:
        result=subprocess.run(["java",str(java),"decrypt",request],input=data,
            stdout=subprocess.PIPE,stderr=subprocess.DEVNULL,timeout=90,check=False)
        if result.returncode!=0 or not 50<len(result.stdout)<180000:
            raise ManualSignFailure("TRANSPORT_DECRYPT_FAILED")
        payload=json.loads(result.stdout)
        if type(payload) is not dict or payload.get("request_id")!=request:
            raise ManualSignFailure("CHANNEL_DENIED")
        return payload
    except (OSError,subprocess.TimeoutExpired,ValueError,UnicodeError,json.JSONDecodeError):
        raise ManualSignFailure("TRANSPORT_UNAVAILABLE")

def _sha_and_size(path):
    sha=hashlib.sha256()
    size=0
    with path.open("rb") as f:
        while True:
            buf=f.read(1024*1024)
            if not buf:break
            sha.update(buf)
            size+=len(buf)
            if size>MAX_APK:raise ManualSignFailure("SOURCE_INVALID")
    return sha.hexdigest(),size

def _metadata(path):
    try:
        x=subprocess.run(["aapt","dump","badging",str(path)],
            stdin=subprocess.DEVNULL,stdout=subprocess.PIPE,stderr=subprocess.DEVNULL,
            timeout=50,check=False)
        if x.returncode or len(x.stdout)>200000:raise ManualSignFailure("SOURCE_INVALID")
        m=re.search(rb"^package: name='([^']+)' versionCode='([0-9]+)' versionName='([^']*)'",x.stdout,re.MULTILINE)
        if not m:raise ManualSignFailure("SOURCE_INVALID")
        app,version_code,version_name=[v.decode("utf-8") for v in m.groups()]
        if not re.fullmatch(r"[A-Za-z0-9_.]{1,180}",app) or len(version_name)>120 or int(version_code)<1:
            raise ManualSignFailure("SOURCE_INVALID")
        return {"applicationId":app,"versionCode":int(version_code),"versionName":version_name}
    except (OSError,subprocess.TimeoutExpired,UnicodeError,ValueError):
        raise ManualSignFailure("SOURCE_INVALID")

def sign(payload):
    allowed={"request_id","variant","alias","keystore","store_password","key_password",
             "source_sha256","source_size"}
    if type(payload) is not dict or set(payload)!=allowed or payload.get("variant")!="MANUAL_APK":
        raise ManualSignFailure("RESULT_MALFORMED")
    request=payload.get("request_id")
    if type(request) is not str or not RX.fullmatch(request) or \
       type(payload.get("source_sha256")) is not str or not SHA.fullmatch(payload["source_sha256"]) or \
       type(payload.get("source_size")) is not int or not 100 < payload["source_size"] <=MAX_APK:
        raise ManualSignFailure("SOURCE_INVALID")
    if type(payload.get("alias")) is not str or not ALIAS.fullmatch(payload["alias"]):
        raise ManualSignFailure("SIGNING_INPUT_DENIED")
    if any(type(payload.get(k)) is not str for k in ("keystore","store_password","key_password")):
        raise ManualSignFailure("SIGNING_INPUT_DENIED")
    if len(payload["keystore"])>4*MAX_KEY_BYTES//3+8:
        raise ManualSignFailure("SIGNING_INPUT_DENIED")
    try:key=bytearray(base64.b64decode(payload["keystore"],validate=True))
    except (ValueError,base64.binascii.Error):raise ManualSignFailure("SIGNING_INPUT_DENIED")
    store=bytearray(payload["store_password"].encode("utf-8"))
    private=bytearray((payload["key_password"] or payload["store_password"]).encode("utf-8"))
    source_sha=payload["source_sha256"]
    source_size=payload["source_size"]
    alias=payload["alias"]
    payload.clear()
    lockfd=None
    try:
        if not 0<len(key)<=MAX_KEY_BYTES or not 0<len(store)<=MAX_PASSWORD or not 0<len(private)<=MAX_PASSWORD:
            raise ManualSignFailure("SIGNING_INPUT_DENIED")
        if not DOWNLOADS.is_dir() or DOWNLOADS.is_symlink() or STATE.is_symlink() or not STATE.is_dir():
            raise ManualSignFailure("STORAGE_UNAVAILABLE")
        lock=STATE/".build-global.lock"
        if lock.is_symlink():raise ManualSignFailure("STORAGE_UNAVAILABLE")
        lockfd=os.open(lock,os.O_CREAT|os.O_RDWR|os.O_NOFOLLOW,0o600)
        try:fcntl.flock(lockfd,fcntl.LOCK_EX|fcntl.LOCK_NB)
        except BlockingIOError:raise ManualSignFailure("JOB_BUSY")
        source=DOWNLOADS/("AnimaDev_unsigned_"+request+".apk")
        if source.is_symlink() or not source.is_file() or source.resolve()!=source.absolute():
            raise ManualSignFailure("SOURCE_INVALID")
        if _sha_and_size(source)!=(source_sha,source_size):
            raise ManualSignFailure("SOURCE_CHANGED")
        expected=_metadata(source)
        # Refuse silently re-signing already signed files.
        verified=subprocess.run(["apksigner","verify",str(source)],
            stdin=subprocess.DEVNULL,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,
            timeout=60,check=False)
        if verified.returncode==0:
            raise ManualSignFailure("ALREADY_SIGNED")
        target=DOWNLOADS/("AnimaDev_signed_"+request+".apk")
        # Android creates this zero-byte MediaStore row in advance. That makes
        # the signed result readable by its owner via ContentResolver after the
        # Termux worker exits, even under Android scoped storage.
        if target.is_symlink() or not target.is_file() or target.resolve()!=target.absolute() or target.stat().st_size!=0:
            raise ManualSignFailure("OUTPUT_EXISTS")
        with tempfile.TemporaryDirectory(prefix="manual-"+request+"-",dir=STATE) as temp:
            aligned=pathlib.Path(temp)/"aligned.apk"
            signed=pathlib.Path(temp)/"signed.apk"
            meta=sign_apk(source,aligned,signed,key,store,private,alias,expected)
            if _sha_and_size(source)!=(source_sha,source_size):
                raise ManualSignFailure("SOURCE_CHANGED")
            digest=hashlib.sha256()
            try:
                fd=os.open(target,os.O_WRONLY|os.O_NOFOLLOW,0o600)
                with os.fdopen(fd,"wb") as out,signed.open("rb") as inp:
                    while True:
                        part=inp.read(1024*1024)
                        if not part:break
                        out.write(part);digest.update(part)
                    out.flush();os.fsync(out.fileno())
                if digest.hexdigest()!=meta["sha256"]:
                    raise ManualSignFailure("OUTPUT_MISMATCH")
            except BaseException:
                try:target.unlink()
                except OSError:pass
                raise
        return {"kind":"MANUAL_APK","path":str(target),"sha256":meta["sha256"],
                "size":meta["size"],"signer_sha256":meta["signer_sha256"],
                "applicationId":meta["applicationId"],"versionCode":meta["versionCode"],
                "versionName":meta["versionName"]}
    finally:
        if lockfd is not None:os.close(lockfd)
        key[:]=b"\0"*len(key);store[:]=b"\0"*len(store);private[:]=b"\0"*len(private)

def main():
    os.umask(0o077)
    request=sys.argv[1] if len(sys.argv)>1 and RX.fullmatch(sys.argv[1]) else ""
    try:
        result=sign(_receive())
        print(json.dumps({"version":1,"request_id":request,"ok":True,
                          "artifact":result},ensure_ascii=False))
    except (ManualSignFailure,SigningError) as exc:
        print(json.dumps({"version":1,"request_id":request,"ok":False,
                          "error_code":exc.code},ensure_ascii=False))
    except Exception:
        print(json.dumps({"version":1,"request_id":request,"ok":False,
                          "error_code":"RESULT_UNCONFIRMED"},ensure_ascii=False))

if __name__=="__main__":
    main()
