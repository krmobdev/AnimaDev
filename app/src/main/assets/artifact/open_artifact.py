"""Open only a checksum-verified build artifact through Termux's URI grant."""
import hashlib
import json
import os
import pathlib
import re
import stat
import subprocess
import sys
import tempfile

DOWNLOADS = pathlib.Path("/storage/emulated/0/Download")
CACHE = pathlib.Path("/data/data/com.termux/files/home/.cache/animadev-open")
OPENER = "/data/data/com.termux/files/usr/bin/termux-open"

def open_artifact(path, expected, size, downloads=DOWNLOADS, cache=CACHE,
                  opener=OPENER, run=subprocess.run):
    source = pathlib.Path(path)
    if (source.parent != downloads or source.parent.resolve() != downloads.absolute()
            or not re.fullmatch(r"[A-Za-z0-9_-]+\.(apk|aab)", source.name)
            or not re.fullmatch(r"[a-f0-9]{64}", expected)
            or not isinstance(size, int) or not 1024 <= size <= 512 * 1024 * 1024):
        raise ValueError("ARTIFACT_INVALID")
    extension = source.suffix
    mime = "application/vnd.android.package-archive" if extension == ".apk" else "application/octet-stream"
    cache.mkdir(parents=True, exist_ok=True, mode=0o700)
    if cache.resolve() != cache.absolute() or not cache.is_dir():
        raise ValueError("ARTIFACT_INVALID")
    os.chmod(cache, 0o700)
    temporary = None
    try:
        fd = os.open(source, os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK)
        with os.fdopen(fd, "rb") as stream:
            info = os.fstat(stream.fileno())
            if not stat.S_ISREG(info.st_mode) or info.st_size != size:
                raise ValueError("ARTIFACT_CHANGED")
            outfd, temporary = tempfile.mkstemp(prefix=".open-", dir=cache)
            digest = hashlib.sha256()
            copied = 0
            with os.fdopen(outfd, "wb") as output:
                while True:
                    block = stream.read(1024 * 1024)
                    if not block:
                        break
                    copied += len(block)
                    if copied > size:
                        raise ValueError("ARTIFACT_CHANGED")
                    digest.update(block)
                    output.write(block)
                output.flush()
                os.fsync(output.fileno())
            if copied != size or digest.hexdigest() != expected:
                raise ValueError("ARTIFACT_CHANGED")
        target = cache / (expected + extension)
        os.replace(temporary, target)
        temporary = None
        try:
            result = run([opener, "--view", "--content-type", mime, str(target)],
                         stdin=subprocess.DEVNULL, stdout=subprocess.DEVNULL,
                         stderr=subprocess.DEVNULL, timeout=15, check=False)
        except FileNotFoundError as exc:
            raise ValueError("OPENER_MISSING") from exc
        if result.returncode != 0:
            raise ValueError("OPEN_FAILED")
        return {"ok": True}
    finally:
        if temporary:
            pathlib.Path(temporary).unlink(missing_ok=True)

if __name__ == "__main__":
    request = ""
    try:
        request, path, digest, size = sys.argv[1:]
        if not re.fullmatch(r"[a-f0-9]{32}", request):
            raise ValueError("ARTIFACT_INVALID")
        result = open_artifact(path, digest, int(size))
        result["request_id"] = request
        print(json.dumps(result))
    except Exception as exc:
        code = str(exc) if isinstance(exc, ValueError) and str(exc) in {
            "ARTIFACT_INVALID", "ARTIFACT_CHANGED", "OPENER_MISSING", "OPEN_FAILED"
        } else "OPEN_FAILED"
        print(json.dumps({"ok": False, "request_id": request, "error_code": code}))
        sys.exit(1)
