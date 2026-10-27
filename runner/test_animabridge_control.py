"""Synthetic, isolated regression tests for the Python controller embedded in AnimaDev.

Every sv/service-daemon/pgrep subprocess is mocked. NEVER touches a real service.
"""

import contextlib
import io
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import textwrap
import unittest
from unittest import mock


KOTLIN = (Path(__file__).resolve().parent.parent
          / "app/src/main/java/com/animacode/animadev/termux/BridgeServiceGateway.kt")


def embedded_script():
    source = KOTLIN.read_text(encoding="utf-8")
    start = source.split('val CONTROL_SCRIPT = """', 1)[1]
    script = textwrap.dedent(start.split('""".trimIndent()', 1)[0])
    compile(script, "<AnimaDev CONTROL_SCRIPT>", "exec")
    return script


class FakeRunit:
    def __init__(self, prefix):
        self.prefix = prefix
        self.service = prefix + "/var/service/animabridge"
        self.active_pid = "111"
        self.next_pid = 200
        self.supervisor = True
        self.manual_running = False

    def run(self, argv, **kwargs):
        cmd = list(argv)
        if cmd[0] == self.prefix + "/bin/pgrep":
            return subprocess.CompletedProcess(cmd, 0 if self.manual_running else 1, "", "")
        if cmd[0] == self.prefix + "/bin/service-daemon":
            self.supervisor = True
            return subprocess.CompletedProcess(cmd, 0, "", "")
        if cmd[0] != self.prefix + "/bin/sv":
            raise AssertionError("Unexpected subprocess: " + repr(cmd))
        action = cmd[1]
        if action == "status":
            if not self.supervisor:
                return subprocess.CompletedProcess(cmd, 1, "", "supervisor-off")
            if self.active_pid:
                text = f"run: {self.service}: (pid {self.active_pid}) 2s, normally down\n"
            else:
                text = f"down: {self.service}: 2s, normally down\n"
            return subprocess.CompletedProcess(cmd, 0, text, "")
        if action == "down":
            self.active_pid = None
        elif action == "up":
            if not self.active_pid:
                self.next_pid += 1
                self.active_pid = str(self.next_pid)
        elif action == "restart":
            self.next_pid += 1
            self.active_pid = str(self.next_pid)
        else:
            raise AssertionError("Unexpected runit action: " + action)
        return subprocess.CompletedProcess(cmd, 0, "", "")


class EmbeddedControllerTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(dir=KOTLIN.parents[8])
        self.addCleanup(self.tmp.cleanup)
        self.root = self.tmp.name
        self.service = Path(self.root) / "var/service/animabridge"
        self.service.mkdir(parents=True)
        self.down = self.service / "down"
        self.fake = FakeRunit(self.root)
        self.script = embedded_script().replace(
            'PREFIX = "/data/data/com.termux/files/usr"',
            "PREFIX = " + repr(self.root),
            1,
        )

    def invoke(self, action):
        stdout, stderr = io.StringIO(), io.StringIO()
        real_readlink = os.readlink

        def safe_readlink(path, *args, **kwargs):
            if str(path).startswith("/proc/"):
                return "/data/data/com.termux/files/usr/bin/python"
            return real_readlink(path, *args, **kwargs)

        with mock.patch.object(subprocess, "run", side_effect=self.fake.run), \
             mock.patch.object(os, "readlink", side_effect=safe_readlink), \
             mock.patch.object(sys, "argv", ["controller", action]), \
             contextlib.redirect_stdout(stdout), contextlib.redirect_stderr(stderr):
            with self.assertRaises(SystemExit) as done:
                exec(compile(self.script, "<synthetic_controller>", "exec"), {"__name__": "__main__"})
        return done.exception.code, stdout.getvalue(), stderr.getvalue()

    def test_status_reports_normally_down_without_changing_service(self):
        self.down.touch()
        pid = self.fake.active_pid
        code, out, _ = self.invoke("status")
        self.assertEqual(code, 0)
        self.assertEqual(out.strip(), "ephemeral: " + self.fake.service)
        self.assertEqual(self.fake.active_pid, pid)
        self.assertTrue(self.down.exists())

    def test_start_arms_persistence_without_restarting_live_agent(self):
        self.down.touch()
        pid = self.fake.active_pid
        code, out, _ = self.invoke("up")
        self.assertEqual(code, 0, out)
        self.assertEqual(self.fake.active_pid, pid)
        self.assertFalse(self.down.exists())

    def test_stop_rejects_restart_then_start_restores_persistent_agent(self):
        code, _, _ = self.invoke("down")
        self.assertEqual(code, 0)
        self.assertTrue(self.down.exists())
        self.assertIsNone(self.fake.active_pid)
        code, _, err = self.invoke("restart")
        self.assertEqual(code, 10)
        self.assertIn("use Start instead", err)
        self.assertIsNone(self.fake.active_pid)
        self.assertTrue(self.down.exists())
        code, out, err = self.invoke("up")
        self.assertEqual(code, 0, err)
        self.assertIn("verified", out)
        self.assertIsNotNone(self.fake.active_pid)
        self.assertFalse(self.down.exists())

    def test_restart_does_not_start_stopped_supervisor(self):
        self.down.touch()
        self.fake.active_pid = None
        self.fake.supervisor = False
        code, _, err = self.invoke("restart")
        self.assertEqual(code, 10)
        self.assertIn("use Start instead", err)
        self.assertFalse(self.fake.supervisor)
        self.assertTrue(self.down.exists())

    def test_supervisor_absent_start_starts_supervisor_safely(self):
        self.down.touch()
        self.fake.active_pid = None
        self.fake.supervisor = False
        code, _, err = self.invoke("up")
        self.assertEqual(code, 0, err)
        self.assertTrue(self.fake.supervisor)
        self.assertFalse(self.down.exists())

    def test_manual_agent_blocks_start_and_retains_disabled_flag(self):
        self.down.touch()
        self.fake.active_pid = None
        self.fake.manual_running = True
        code, _, err = self.invoke("up")
        self.assertEqual(code, 7)
        self.assertIn("Manual agent", err)
        self.assertTrue(self.down.exists())
        self.assertIsNone(self.fake.active_pid)

    def test_restart_changes_pid_when_already_running(self):
        self.down.touch()
        previous = self.fake.active_pid
        code, out, err = self.invoke("restart")
        self.assertEqual(code, 0, err)
        self.assertNotEqual(self.fake.active_pid, previous)
        self.assertIn("verified", out)
        self.assertFalse(self.down.exists())


if __name__ == "__main__":
    unittest.main()
