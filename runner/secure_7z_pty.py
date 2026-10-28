"""7-Zip password prompt driver. Never puts secrets in argv/env, files or logs.

Waits for exact interactive password prompts over a private PTY and fails closed
if no password prompt occurs. This module does not itself create key material.
"""
import errno
import os
import pty
import re
import select
import subprocess
import time

_PROMPT = re.compile(rb"(?:enter|verify|re.?enter|confirm)\s+(?:the\s+)?password[^:\r\n]{0,70}:\s*$|password:\s*$", re.I)


class SecureArchiveError(Exception):
    pass


def run_sevenzip_password(argv, password: str, cwd, timeout=120, minimum_prompts=1):
    if not isinstance(password, str) or not 12 <= len(password) <= 256 or "\n" in password or "\r" in password:
        raise SecureArchiveError("Invalid password")
    if any(password in str(arg) for arg in argv):
        raise SecureArchiveError("Password leaked to argv")
    master, slave = pty.openpty()
    proc = None
    prompts = 0
    tail = bytearray()
    try:
        proc = subprocess.Popen(argv, cwd=cwd, stdin=slave, stdout=slave, stderr=slave,
                                close_fds=True, start_new_session=True)
        os.close(slave)
        slave = -1
        deadline = time.monotonic() + timeout
        while True:
            if time.monotonic() > deadline:
                raise SecureArchiveError("Interactive 7z timeout")
            readable, _, _ = select.select([master], [], [], 0.15)
            if readable:
                try:
                    chunk = os.read(master, 4096)
                except OSError as ex:
                    if ex.errno != errno.EIO:
                        raise
                    chunk = b""
                if chunk:
                    # Only hold bounded diagnostic output in memory; never print it.
                    tail.extend(chunk)
                    if len(tail) > 320:
                        tail = tail[-320:]
                    normalized = bytes(tail).replace(b"\r", b"\n")
                    if _PROMPT.search(normalized):
                        prompts += 1
                        if prompts > 3:
                            raise SecureArchiveError("Unexpected password prompt count")
                        os.write(master, password.encode("utf-8") + b"\n")
                        tail.clear()
                elif proc.poll() is not None:
                    break
            if proc.poll() is not None and not readable:
                break
        if proc.wait(timeout=4) != 0 or prompts < minimum_prompts:
            raise SecureArchiveError("7z password encryption or verification not confirmed")
    finally:
        if proc is not None and proc.poll() is None:
            proc.kill()
            proc.wait()
        if slave >= 0:
            os.close(slave)
        os.close(master)

# Names consumed by the one-shot key generator.
ArchivePasswordError = SecureArchiveError
invoke = run_sevenzip_password
