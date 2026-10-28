"""AnimaDev proposed one-shot private JKS creation + AES-256 7z backup engine.

INTEGRATION NOTE: This is a standalone, NOT YET INSTALLED module. It must be
injected into AnimaDev's existing encrypted ReleaseTransport pipeline, with an
Android app-generated random JKS passphrase supplied inside that envelope.
All output files must be pre-created as zero-length *Android-owned* Downloads
MediaStore rows, then copied to app-private internal storage and deleted.

No password is passed in argv/env or printed. No user key is read.
"""

import hashlib
import json
import os
import pathlib
import re
import secrets
import base64
import fcntl
import sys
import shutil
import subprocess
import tempfile
import time
from dataclasses import dataclass
# secure_7z_pty.py source is prepended by the Android encrypted command launcher.
# Do not import a dynamic module from Termux user paths.

ALIAS = re.compile(r"^[A-Za-z0-9_.-]{1,100}$")
REQUEST_ID = re.compile(r"^[a-f0-9]{32}$")
BITS = frozenset((2048, 3072, 4096))
MAX_KEY_BYTES = 96 * 1024
MAX_ARCHIVE_BYTES = 2 * 1024 * 1024
MAX_PASSWORD = 256


class GenerateFailure(Exception):
    def __init__(self, code: str):
        super().__init__(code)
        self.code = code


@dataclass(frozen=True)
class KeySpec:
    request_id: str
    alias: str
    bits: int
    jks_password: str
    archive_password: str

    def validate(self) -> None:
        if not REQUEST_ID.fullmatch(self.request_id):
            raise GenerateFailure("REQUEST_INVALID")
        if not ALIAS.fullmatch(self.alias) or type(self.bits) is not int or self.bits not in BITS:
            raise GenerateFailure("KEY_SPEC_INVALID")
        if not (16 <= len(self.jks_password) <= MAX_PASSWORD):
            raise GenerateFailure("JKS_PASSWORD_INVALID")
        if not (12 <= len(self.archive_password) <= MAX_PASSWORD) or "\n" in self.archive_password or "\r" in self.archive_password:
            raise GenerateFailure("ARCHIVE_PASSWORD_INVALID")
        if self.archive_password == self.jks_password:
            raise GenerateFailure("PASSWORD_REUSE_FORBIDDEN")


def _memfd(name: str, data: bytes) -> int:
    if not hasattr(os, "memfd_create"):
        import ctypes
        libc = ctypes.CDLL(None, use_errno=True)
        func = getattr(libc, "memfd_create", None)
        if func is None:
            raise GenerateFailure("MEMFD_UNAVAILABLE")
        func.argtypes = (ctypes.c_char_p, ctypes.c_uint)
        func.restype = ctypes.c_int
        fd = func(name.encode("ascii"), 0)
        if fd < 0:
            raise GenerateFailure("MEMFD_UNAVAILABLE")
    else:
        fd = os.memfd_create(name, flags=0)
    try:
        os.write(fd, data)
        os.lseek(fd, 0, os.SEEK_SET)
        return fd
    except Exception:
        os.close(fd)
        raise


def _tool(args: list[str], *, cwd: pathlib.Path | None = None,
          input_data: bytes | None = None, pass_fds: tuple[int, ...] = (),
          timeout: int = 120) -> None:
    # Deliberately suppress output (some tools echo diagnostics about input).
    try:
        done = subprocess.run(args, cwd=cwd, input=input_data,
                              stdin=subprocess.DEVNULL if input_data is None else None,
                              pass_fds=pass_fds, stdout=subprocess.DEVNULL,
                              stderr=subprocess.DEVNULL, timeout=timeout, check=False)
    except (OSError, subprocess.TimeoutExpired):
        raise GenerateFailure("TOOL_UNAVAILABLE") from None
    if done.returncode != 0:
        raise GenerateFailure("TOOL_FAILED")


def _sha256(path: pathlib.Path) -> str:
    with path.open("rb") as handle:
        return hashlib.file_digest(handle, "sha256").hexdigest()


def _check_private_dir(folder: pathlib.Path) -> None:
    if folder.is_symlink() or not folder.is_dir() or folder.resolve() != folder.absolute():
        raise GenerateFailure("PRIVATE_DIRECTORY_UNAVAILABLE")


def _precreated_output(path: pathlib.Path) -> None:
    if path.is_symlink() or not path.is_file() or path.stat().st_size != 0 or path.resolve() != path.absolute():
        raise GenerateFailure("OUTPUT_NOT_EMPTY")


def _copy_precreated(source: pathlib.Path, target: pathlib.Path) -> None:
    _precreated_output(target)
    fd = os.open(target, os.O_WRONLY | os.O_NOFOLLOW)
    try:
        with os.fdopen(fd, "wb") as out, source.open("rb") as inp:
            shutil.copyfileobj(inp, out, 1024 * 1024)
            out.flush()
            os.fsync(out.fileno())
    except BaseException:
        try:
            target.unlink()
        except OSError:
            pass
        raise
    if _sha256(source) != _sha256(target):
        try:
            target.unlink()
        except OSError:
            pass
        raise GenerateFailure("COPY_MISMATCH")


def create_backup(spec: KeySpec, workdir: pathlib.Path,
                  key_output: pathlib.Path, archive_output: pathlib.Path,
                  *, sevenzip: str = "7z", keytool: str = "keytool") -> dict:
    """Generate a fresh JKS and an encrypted, integrity-tested 7z backup.

    Outputs are expected to be zero-sized, app-owned files. The caller must
    validate exact expected download filenames against spec.request_id first.
    """
    spec.validate()
    workdir = pathlib.Path(workdir)
    _check_private_dir(workdir)
    _precreated_output(key_output)
    _precreated_output(archive_output)
    with tempfile.TemporaryDirectory(prefix=".animadev-keygen-", dir=workdir) as folder:
        tmp = pathlib.Path(folder)
        os.chmod(tmp, 0o700)
        key = tmp / "signing.jks"
        manifest = tmp / "recovery.json"
        archive = tmp / "backup.7z"
        # Store + key password only in anonymous RAM fd, never argv, temp file or env.
        fd = _memfd("keygen-password", spec.jks_password.encode("utf-8") + b"\n")
        try:
            _tool([keytool, "-genkeypair", "-alias", spec.alias,
                   "-keyalg", "RSA", "-keysize", str(spec.bits),
                   "-sigalg", "SHA256withRSA", "-validity", "10000",
                   "-dname", "CN=AnimaDev Generated Signing Key",
                   "-keystore", str(key), "-storetype", "JKS",
                   "-storepass:file", f"/proc/self/fd/{fd}",
                   "-keypass:file", f"/proc/self/fd/{fd}", "-noprompt"],
                  pass_fds=(fd,), timeout=180)
        finally:
            os.close(fd)
        if not key.is_file() or not 100 <= key.stat().st_size <= MAX_KEY_BYTES:
            raise GenerateFailure("JKS_GENERATION_FAILED")
        payload = {"format": "animadev-jks-backup-v1", "alias": spec.alias,
                   "algorithm": "RSA", "key_bits": spec.bits,
                   "store_password": spec.jks_password,
                   "key_password": spec.jks_password,
                   "keystore_sha256": _sha256(key),
                   "created_unix": int(time.time())}
        fd = os.open(manifest, os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600)
        with os.fdopen(fd, "wb") as out:
            out.write(json.dumps(payload, ensure_ascii=False, separators=(",", ":")).encode("utf-8"))
        # Password is passed only over a private interactive PTY, not argv/env.
        # Fail closed if 7z does not actually request a password.
        try:
            invoke([sevenzip, "a", "-t7z", "-mhe=on", "-p", "-y", str(archive),
                    "signing.jks", "recovery.json"], spec.archive_password, tmp, timeout=180)
        except ArchivePasswordError:
            raise GenerateFailure("ARCHIVE_ENCRYPTION_FAILED") from None
        if not archive.is_file() or not 200 < archive.stat().st_size <= MAX_ARCHIVE_BYTES:
            raise GenerateFailure("ARCHIVE_GENERATION_FAILED")
        try:
            invoke([sevenzip, "t", "-y", str(archive)], spec.archive_password,
                   tmp, timeout=180)
        except ArchivePasswordError:
            raise GenerateFailure("ARCHIVE_VERIFICATION_FAILED") from None
        # Fail closed if the archive can also be read with the wrong password.
        wrong = secrets.token_urlsafe(32)
        if wrong == spec.archive_password:
            raise GenerateFailure("ARCHIVE_VERIFICATION_FAILED")
        try:
            invoke([sevenzip, "t", "-y", str(archive)], wrong,
                   tmp, timeout=30)
        except ArchivePasswordError:
            pass  # Expected: bad password cannot decrypt archive.
        else:
            raise GenerateFailure("ARCHIVE_ENCRYPTION_FAILED")
        # Key is encrypted by a separate generated JKS password; archive is AES-256.
        _copy_precreated(key, key_output)
        try:
            _copy_precreated(archive, archive_output)
        except BaseException:
            try:
                key_output.unlink()
            except OSError:
                pass
            raise
        return {"request_id": spec.request_id, "kind": "JKS_BACKUP",
                "alias": spec.alias, "key_bits": spec.bits,
                "jks_sha256": _sha256(key_output), "jks_size": key_output.stat().st_size,
                "archive_sha256": _sha256(archive_output), "archive_size": archive_output.stat().st_size}


# Only authenticated RSA/AES-GCM envelope reaches this entrypoint.
HOME = pathlib.Path("/data/data/com.termux/files/home")
STATE = HOME / ".local/share/animadev"
DOWNLOADS = pathlib.Path("/storage/emulated/0/Download")

def receive():
    if len(sys.argv) != 3 or not REQUEST_ID.fullmatch(sys.argv[1]) or not re.fullmatch(r"[a-f0-9]{64}", sys.argv[2]):
        raise GenerateFailure("CHANNEL_DENIED")
    request, expected = sys.argv[1:]
    transport = STATE / "ReleaseTransport.java"
    if transport.is_symlink() or not transport.is_file() or _sha256(transport) != expected:
        raise GenerateFailure("CHANNEL_DENIED")
    ciphertext = sys.stdin.buffer.read(230001)
    if not 500 <= len(ciphertext) <= 230000:
        raise GenerateFailure("ENVELOPE_MISSING")
    try:
        call = subprocess.run(["java", str(transport), "decrypt", request], input=ciphertext,
                              stdout=subprocess.PIPE, stderr=subprocess.DEVNULL,
                              timeout=90, check=False)
        if call.returncode or not 50 < len(call.stdout) <= 180000:
            raise GenerateFailure("TRANSPORT_DECRYPT_FAILED")
        value = json.loads(call.stdout)
        if type(value) is not dict or value.get("request_id") != request:
            raise GenerateFailure("CHANNEL_DENIED")
        return value
    except (OSError, subprocess.TimeoutExpired, ValueError, UnicodeError):
        raise GenerateFailure("TRANSPORT_UNAVAILABLE")

def handle(data):
    required = {"request_id", "variant", "alias", "bits", "jks_password", "archive_password"}
    if type(data) is not dict or set(data) != required or data.get("variant") != "GENERATE_JKS":
        raise GenerateFailure("RESULT_MALFORMED")
    spec = KeySpec(data["request_id"], data["alias"], data["bits"], data["jks_password"], data["archive_password"])
    spec.validate()
    data.clear()
    if not DOWNLOADS.is_dir() or DOWNLOADS.is_symlink() or not STATE.is_dir() or STATE.is_symlink():
        raise GenerateFailure("STORAGE_UNAVAILABLE")
    lock = STATE / ".build-global.lock"
    if lock.is_symlink():
        raise GenerateFailure("STORAGE_UNAVAILABLE")
    fd = os.open(lock, os.O_RDWR | os.O_CREAT | os.O_NOFOLLOW, 0o600)
    try:
        try:
            fcntl.flock(fd, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            raise GenerateFailure("JOB_BUSY")
        jks = DOWNLOADS / ("AnimaDev_gen_" + spec.request_id + ".jks")
        archive = DOWNLOADS / ("AnimaDev_backup_" + spec.request_id + ".7z")
        return create_backup(spec, STATE, jks, archive)
    finally:
        os.close(fd)

def main():
    os.umask(0o077)
    request = sys.argv[1] if len(sys.argv) > 1 and REQUEST_ID.fullmatch(sys.argv[1]) else ""
    try:
        result = handle(receive())
        print(json.dumps({"version":1,"request_id":request,"ok":True,"artifact":result}))
    except GenerateFailure as exc:
        print(json.dumps({"version":1,"request_id":request,"ok":False,"error_code":exc.code}))
    except Exception:
        print(json.dumps({"version":1,"request_id":request,"ok":False,"error_code":"RESULT_UNCONFIRMED"}))

if __name__ == "__main__":
    main()
