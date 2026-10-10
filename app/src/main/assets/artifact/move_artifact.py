"""Checksum-verified Release AAB relocation in shared primary storage.

This standalone command has no access to JKS, passwords, Git or build execution.
The original Downloads file is retained if anything goes wrong.
"""
import hashlib
import json
import os
import pathlib
import re
import stat
import sys
import tempfile

ROOT = "/storage/emulated/0/"
DOWNLOADS = pathlib.Path(ROOT + "Download")

def relocate(source_text, expected, size, output_dir):
    source = pathlib.Path(source_text)
    if (source.parent != DOWNLOADS
            or not re.fullmatch(r"[A-Za-z0-9_-]+[.]aab", source.name)
            or not re.fullmatch(r"[a-f0-9]{64}", expected)
            or not isinstance(size, int) or not 1024 <= size <= 512 * 1024 * 1024):
        raise ValueError("ARTIFACT_INVALID")
    if (not isinstance(output_dir, str) or len(output_dir) > 520
            or not output_dir.startswith(ROOT)
            or not all(re.fullmatch(r"[\w ().&-]{1,80}",part) and part not in (".","..")
                       for part in output_dir[len(ROOT):].split("/"))):
        raise ValueError("DESTINATION_INVALID")
    folder = pathlib.Path(output_dir)
    if (not folder.is_dir() or folder.is_symlink()
            or folder.resolve() != folder.absolute()):
        raise ValueError("DESTINATION_UNAVAILABLE")
    target = folder / source.name
    if target != source and (target.exists() or target.is_symlink()):
        raise ValueError("DESTINATION_EXISTS")
    temporary = None
    try:
        fd = os.open(source, os.O_RDONLY | os.O_NOFOLLOW)
        with os.fdopen(fd, "rb") as inp:
            s = os.fstat(inp.fileno())
            if not stat.S_ISREG(s.st_mode) or s.st_size != size:
                raise ValueError("ARTIFACT_CHANGED")
            digest = hashlib.sha256()
            if target == source:
                while True:
                    chunk = inp.read(1024 * 1024)
                    if not chunk:
                        break
                    digest.update(chunk)
            else:
                out_fd, temporary = tempfile.mkstemp(prefix=".animadev-move-", dir=folder)
                with os.fdopen(out_fd, "wb") as out:
                    while True:
                        chunk = inp.read(1024 * 1024)
                        if not chunk:
                            break
                        digest.update(chunk)
                        out.write(chunk)
                    out.flush()
                    os.fsync(out.fileno())
            if digest.hexdigest() != expected:
                raise ValueError("ARTIFACT_CHANGED")
            if target != source:
                if target.exists() or target.is_symlink():
                    raise ValueError("DESTINATION_EXISTS")
                os.rename(temporary, target)
                temporary = None
                # Only delete the exact source after a fully verified copy exists.
                if source.stat().st_ino == s.st_ino and source.stat().st_size == size:
                    source.unlink()
        return str(target)
    finally:
        if temporary:
            pathlib.Path(temporary).unlink(missing_ok=True)

if __name__ == "__main__":
    request = ""
    try:
        request, source, sha, size, destination = sys.argv[1:]
        if not re.fullmatch(r"[a-f0-9]{32}", request):
            raise ValueError("ARTIFACT_INVALID")
        path = relocate(source, sha, int(size), destination)
        print(json.dumps({"ok": True, "request_id": request, "path": path}))
    except Exception as exc:
        code = str(exc) if isinstance(exc, ValueError) and str(exc) in {
            "ARTIFACT_INVALID", "ARTIFACT_CHANGED", "DESTINATION_INVALID",
            "DESTINATION_UNAVAILABLE", "DESTINATION_EXISTS"
        } else "EXPORT_FAILED"
        print(json.dumps({"ok": False, "request_id": request, "error_code": code}))
        sys.exit(1)
