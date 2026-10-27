"""Release AAB signing primitives. Never put keystore bytes/passwords in argv, env or logs.

Credentials must arrive in a short-lived authenticated local IPC request. This module
does not accept a filesystem path to the user's keystore and never persists it.
It is deliberately NOT a standalone network/CLI release entrypoint.
"""
import hashlib
import os
import pathlib
import re
import subprocess
import zipfile

MAX_KEY_BYTES = 96 * 1024
MAX_PASSWORD = 1024
ALIAS = re.compile(r"^[A-Za-z0-9_.-]{1,100}$")
BUNDLE_LIMIT = 512 * 1024 * 1024

class SigningError(Exception):
    def __init__(self, code):
        super().__init__(code)
        self.code = code

def _create_memfd(name):
    if hasattr(os, "memfd_create"):
        return os.memfd_create(name,flags=0)
    # Python on Android/Termux may omit this binding although bionic libc supports it.
    import ctypes
    libc=ctypes.CDLL(None,use_errno=True)
    fn=getattr(libc,"memfd_create",None)
    if fn is None:
        raise SigningError("MEMFD_UNAVAILABLE")
    fn.argtypes=(ctypes.c_char_p,ctypes.c_uint)
    fn.restype=ctypes.c_int
    fd=fn(name.encode("ascii"),0)
    if fd<0:
        raise SigningError("MEMFD_UNAVAILABLE")
    return fd

def _ram_fd(label, data):
    fd = _create_memfd("animadev-"+label)
    try:
        # Android bionic memfd is anonymous, and SELinux denies fchmod on it.
        view = memoryview(data)
        try:
            offset = 0
            while offset < len(view):
                offset += os.write(fd, view[offset:])
        finally:
            view.release()
        os.lseek(fd, 0, os.SEEK_SET)
        return fd
    except BaseException:
        os.close(fd)
        raise

def _verify_bundle(path):
    if path.is_symlink() or not path.is_file() or path.stat().st_size > BUNDLE_LIMIT:
        raise SigningError("ARTIFACT_INVALID")
    try:
        with zipfile.ZipFile(path) as archive:
            files=set(archive.namelist())
            if "BundleConfig.pb" not in files or not any(x.endswith("/manifest/AndroidManifest.xml") for x in files):
                raise SigningError("ARTIFACT_INVALID")
    except (OSError, zipfile.BadZipFile):
        raise SigningError("ARTIFACT_INVALID")

def sign_bundle(unsigned, signed, key_data, store_password, key_password, alias, jarsigner="jarsigner", timeout=180):
    """Sign one freshly created AAB with JDK jarsigner; no keystore/password temp files.

    AAB and signed output may be regular files, but the JKS and passwords reside
    only in anonymous Linux memfd objects passed directly to the signer process.
    """
    unsigned, signed = pathlib.Path(unsigned), pathlib.Path(signed)
    if (not isinstance(key_data, bytearray) or not 0 < len(key_data) <= MAX_KEY_BYTES
            or not isinstance(store_password, bytearray) or not 0 < len(store_password) <= MAX_PASSWORD
            or not isinstance(key_password, bytearray) or not 0 < len(key_password) <= MAX_PASSWORD
            or not ALIAS.fullmatch(alias)):
        raise SigningError("SIGNING_INPUT_DENIED")
    if signed.exists() or signed.is_symlink() or signed.parent.resolve() != signed.parent.absolute():
        raise SigningError("ARTIFACT_PATH_DENIED")
    if unsigned.resolve() != unsigned.absolute():
        raise SigningError("ARTIFACT_PATH_DENIED")
    _verify_bundle(unsigned)
    fds=[]
    try:
        fds=[_ram_fd("jks",key_data)]
        fds.append(_ram_fd("store-password",store_password+b"\n"))
        fds.append(_ram_fd("key-password",key_password+b"\n"))
        cmd=[jarsigner, "-keystore", "/proc/self/fd/"+str(fds[0]), "-storetype","JKS",
             "-storepass:file","/proc/self/fd/"+str(fds[1]),
             "-keypass:file","/proc/self/fd/"+str(fds[2]),
             "-signedjar",str(signed),str(unsigned),alias]
        # Never forward stderr/stdout from jarsigner: it can include sensitive diagnostics.
        try:
            result=subprocess.run(cmd,pass_fds=tuple(fds),stdin=subprocess.DEVNULL,
                                  stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,
                                  timeout=timeout,check=False)
        except subprocess.TimeoutExpired:
            raise SigningError("SIGNING_TIMEOUT")
        except OSError:
            raise SigningError("JARSIGNER_UNAVAILABLE")
        if result.returncode != 0:
            raise SigningError("KEY_OR_SIGNING_FAILED")
    finally:
        for fd in fds:
            os.close(fd)
    try:
        _verify_bundle(signed)
        # jarsigner -verify can exit successfully for certain unsigned archives.
        # Require both a manifest signature and a signature block explicitly.
        with zipfile.ZipFile(signed) as zipfile_signed:
            members={name.upper() for name in zipfile_signed.namelist()}
            signature_files={n for n in members if n.startswith("META-INF/") and n.endswith(".SF")}
            signature_blocks={n for n in members if n.startswith("META-INF/") and n.endswith((".RSA",".DSA",".EC"))}
            if not signature_files or not signature_blocks:
                raise SigningError("SIGNATURE_INVALID")
        result=subprocess.run([jarsigner,"-verify",str(signed)],
            stdin=subprocess.DEVNULL,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,
            timeout=60,check=False)
        if result.returncode != 0 or not signed.is_file():
            raise SigningError("SIGNATURE_INVALID")
    except (subprocess.TimeoutExpired, OSError):
        raise SigningError("SIGNATURE_INVALID")
    with signed.open("rb") as stream:
        digest=hashlib.file_digest(stream,"sha256").hexdigest()
    return {"sha256":digest,"size":signed.stat().st_size}


def _verify_apk_archive(path):
    if (path.is_symlink() or not path.is_file() or path.stat().st_size > BUNDLE_LIMIT
            or path.resolve() != path.absolute()):
        raise SigningError("ARTIFACT_INVALID")
    try:
        with zipfile.ZipFile(path) as archive:
            if "AndroidManifest.xml" not in archive.namelist():
                raise SigningError("ARTIFACT_INVALID")
    except (OSError, zipfile.BadZipFile):
        raise SigningError("ARTIFACT_INVALID")


def sign_apk(unsigned, aligned, signed, key_data, store_password, key_password,
             alias, expected, zipalign="zipalign", apksigner="apksigner", aapt="aapt"):
    """Zipalign, APK-Signature sign and verify with the same anonymous JKS IPC."""
    unsigned, aligned, signed = map(pathlib.Path, (unsigned, aligned, signed))
    if (not isinstance(key_data, bytearray) or not 0 < len(key_data) <= MAX_KEY_BYTES
            or not isinstance(store_password, bytearray) or not 0 < len(store_password) <= MAX_PASSWORD
            or not isinstance(key_password, bytearray) or not 0 < len(key_password) <= MAX_PASSWORD
            or not ALIAS.fullmatch(alias)):
        raise SigningError("SIGNING_INPUT_DENIED")
    _verify_apk_archive(unsigned)
    for output in (aligned, signed):
        if output.exists() or output.is_symlink() or output.parent.resolve()!=output.parent.absolute():
            raise SigningError("ARTIFACT_PATH_DENIED")
    try:
        aligned_result=subprocess.run([zipalign,"-f","4",str(unsigned),str(aligned)],
            stdin=subprocess.DEVNULL, stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,timeout=120,check=False)
        if aligned_result.returncode!=0:
            raise SigningError("ZIPALIGN_FAILED")
        _verify_apk_archive(aligned)
    except (OSError, subprocess.TimeoutExpired):
        raise SigningError("ZIPALIGN_UNAVAILABLE")
    fds=[]
    try:
        fds=[_ram_fd("jks",key_data)]
        fds.append(_ram_fd("apk-store-pass",store_password+b"\n"))
        fds.append(_ram_fd("apk-key-pass",key_password+b"\n"))
        command=[apksigner,"sign","--ks","/proc/self/fd/"+str(fds[0]),
            "--ks-type","JKS","--ks-key-alias",alias,
            "--ks-pass","file:/proc/self/fd/"+str(fds[1]),
            "--key-pass","file:/proc/self/fd/"+str(fds[2]),
            "--out",str(signed),str(aligned)]
        try:
            result=subprocess.run(command,pass_fds=tuple(fds),stdin=subprocess.DEVNULL,
                stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,timeout=180,check=False)
        except (OSError,subprocess.TimeoutExpired):
            raise SigningError("APKSIGNER_UNAVAILABLE")
        if result.returncode!=0:raise SigningError("KEY_OR_SIGNING_FAILED")
    finally:
        for fd in fds:os.close(fd)
    _verify_apk_archive(signed)
    try:
        verification=subprocess.run([apksigner,"verify","--verbose","--print-certs",str(signed)],
            stdin=subprocess.DEVNULL,stdout=subprocess.PIPE,stderr=subprocess.DEVNULL,
            timeout=90,check=False)
        if verification.returncode!=0 or len(verification.stdout)>16384:
            raise SigningError("APK_SIGNATURE_INVALID")
        fingerprint=re.search(rb"Signer #1 certificate SHA-256 digest:\s*([a-fA-F0-9]{64})",
                              verification.stdout)
        if not fingerprint:
            raise SigningError("APK_SIGNATURE_INVALID")
        cert=fingerprint.group(1).decode("ascii").lower()
        inspection=subprocess.run([aapt,"dump","badging",str(signed)],
            stdin=subprocess.DEVNULL,stdout=subprocess.PIPE,stderr=subprocess.DEVNULL,
            timeout=60,check=False)
        if inspection.returncode!=0 or len(inspection.stdout)>250000:
            raise SigningError("APK_METADATA_INVALID")
        manifest=re.search(rb"^package: name='([^']+)' versionCode='([0-9]+)' versionName='([^']*)'",
                           inspection.stdout,re.MULTILINE)
        if not manifest:
            raise SigningError("APK_METADATA_INVALID")
        app_id,version_code,version_name=[v.decode("utf-8") for v in manifest.groups()]
        if (app_id!=expected["applicationId"] or int(version_code)!=int(expected["versionCode"])
                or version_name!=str(expected["versionName"])):
            raise SigningError("APK_METADATA_INVALID")
    except (OSError,subprocess.TimeoutExpired,UnicodeError):
        raise SigningError("APK_VERIFICATION_UNAVAILABLE")
    with signed.open("rb") as stream:
        digest=hashlib.file_digest(stream,"sha256").hexdigest()
    return {"sha256":digest,"size":signed.stat().st_size,
            "signer_sha256":cert,"applicationId":app_id,
            "versionCode":int(version_code),"versionName":version_name}
