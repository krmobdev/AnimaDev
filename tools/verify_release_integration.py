"""Explicit, synthetic end-to-end release check. Not part of regular unit suite.

Runs the full Git-checked offline Gradle AAB build + in-memory JKS signing
inside Termux. No real/user JKS is read, and no file is put into Downloads.
"""
import hashlib
import os
import pathlib
import subprocess
import tempfile
import zipfile

HOME=pathlib.Path("/data/data/com.termux/files/home")
ROOT=pathlib.Path(__file__).resolve().parent.parent
WORKER=ROOT/"runner/release_worker.py"
SIGNER=ROOT/"runner/release_signer.py"

def main():
    script=SIGNER.read_text()+"\n"+WORKER.read_text()
    scope={"__name__":"release_e2e_test"}
    exec(compile(script,"release-worker-integrated","exec"),scope)
    project={"id":hashlib.sha256(str(ROOT).encode()).hexdigest()[:32],
        "root":str(ROOT),"name":"AnimaDevSynthetic",
        "remote":"github.com/krmobdev/AnimaDev",
        "branch":"feature/termux-probe"}
    with tempfile.TemporaryDirectory(prefix="animadev-fixture-") as folder:
        keypath=pathlib.Path(folder)/"synthetic.jks"
        cmd=["keytool","-genkeypair","-storetype","JKS",
             "-keystore",str(keypath),"-storepass","synthetic-store",
             "-keypass","synthetic-key","-alias","fixture",
             "-dname","CN=AnimaDev Non-Production Test",
             "-keyalg","RSA","-keysize","2048","-validity","1","-noprompt"]
        result=subprocess.run(cmd,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,timeout=50)
        if result.returncode!=0:raise RuntimeError("Synthetic fixture failed")
        import base64
        encoded=base64.b64encode(keypath.read_bytes()).decode("ascii")
        keypath.unlink() # Not a production key; no JKS is stored during Gradle or signing.
        payload={"request_id":os.urandom(16).hex(),"project":project,"alias":"fixture",
                 "keystore":encoded,"store_password":"synthetic-store",
                 "key_password":"synthetic-key"}
        captured=[]
        def test_export(signed, request, profile, sha, metadata):
            with zipfile.ZipFile(signed) as z:
                if not "BundleConfig.pb" in z.namelist():raise RuntimeError("Invalid bundle")
                if not any(name.endswith(".SF") for name in z.namelist()):
                    raise RuntimeError("No signature")
            digest=hashlib.sha256(signed.read_bytes()).hexdigest()
            if digest!=metadata["sha256"]:raise RuntimeError("Hash mismatch")
            captured.append((sha,digest,metadata["size"]))
            # Return metadata only: do NOT write a test-signed AAB to Downloads.
            return {"kind":"SYNTHETIC_SIGNED_AAB","sha256":digest,"size":metadata["size"],"commit":sha}
        scope["_export"]=test_export
        result=scope["run"](payload)
        if len(captured)!=1 or result["kind"]!="SYNTHETIC_SIGNED_AAB":
            raise RuntimeError("End-to-end result not confirmed")
        print("SYNTHETIC_RELEASE_VERIFIED")
        print("git_sha="+result["commit"])
        print("aab_sha256="+result["sha256"])
        print("aab_size="+str(result["size"]))
        print("user_keystore_accessed=false")
        print("download_export=false")

if __name__=="__main__":
    main()
