"""Offline safety regression for AnimaDev's embedded Termux Python scripts.

These tests use a temporary SQLite database and a stubbed Git runner.
No existing Git checkout, Termux journal, GitHub refs or Android settings change.
"""
import importlib.util
import pathlib
import tempfile
import unittest
import uuid
from unittest.mock import patch

BASE = pathlib.Path(__file__).resolve().parents[1]


def load(name, path):
    spec = importlib.util.spec_from_file_location(name, BASE / path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


class RunnerHistoryTests(unittest.TestCase):
    def test_existing_one_hundred_jobs_can_grow_without_deletion(self):
        runner = load("anima_jobs", "app/src/main/assets/runner/animadev_runner.py")
        with tempfile.TemporaryDirectory(prefix="anima-history-") as temporary:
            engine = runner.Engine(root=pathlib.Path(temporary))
            ids = []
            for _ in range(105):
                jid = uuid.uuid4().hex
                job = engine.query(jid, "animaquiz", "CHECK")
                self.assertEqual(job["state"], "NOT_STARTED")
                ids.append(jid)
            with engine.store.transaction() as conn:
                count = conn.execute("SELECT COUNT(*) FROM jobs").fetchone()[0]
                first = engine.store.get(conn, ids[0])
                last = engine.store.get(conn, ids[-1])
            self.assertEqual(count, 105)
            self.assertEqual(first["job_id"], ids[0])
            self.assertEqual(last["job_id"], ids[-1])
            self.assertEqual(engine.query(ids[0], "animaquiz", "CHECK")["job_id"], ids[0])


class LocalBranchSwitchTests(unittest.TestCase):
    def setUp(self):
        self.branch = load("anima_branch", "app/src/main/assets/catalog/branch_switch.py")
        self.temp = tempfile.TemporaryDirectory(prefix="anima-branch-")
        self.addCleanup(self.temp.cleanup)
        self.root = pathlib.Path(self.temp.name)
        (self.root / ".git").mkdir()
        self.branch.ROOT = self.root
        self.current = "a" * 40
        self.target = "b" * 40
        self.visited = []
        self.active = "main"

    def git(self, args, timeout=70):
        self.visited.append(args)
        if args == ["rev-parse", "--show-toplevel"]: return str(self.root)
        if args == ["remote", "get-url", "origin"]: return "git@github.com:krmobdev/quizapp.git"
        if args == ["symbolic-ref", "--quiet", "--short", "HEAD"]: return self.active
        if args == ["rev-parse", "HEAD"]: return self.target if self.active != "main" else self.current
        if args == ["status", "--porcelain=v1", "--untracked-files=all"]: return ""
        if args == ["show-ref", "--verify", "refs/heads/archive/local-only"]: return self.target
        if args == ["rev-parse", "refs/heads/archive/local-only"]: return self.target
        if args == ["switch", "--no-guess", "archive/local-only"]:
            self.active = "archive/local-only"
            return ""
        if args and args[0] == "ls-remote":
            raise AssertionError("Local-only checkout must never contact network")
        raise AssertionError(f"Unexpected git command: {args!r}")

    def test_local_only_sha_verified_without_network_or_reset(self):
        with patch.object(self.branch, "run", side_effect=self.git):
            result = self.branch.switch("archive/local-only", self.current, self.target,
                                        "github.com/krmobdev/quizapp", local_only=True)
        self.assertEqual(result["commit"], self.target)
        self.assertEqual(self.active, "archive/local-only")
        self.assertFalse(any(args[0] in ("ls-remote", "fetch", "push", "reset", "clean")
                             for args in self.visited))

    def test_dirty_local_checkout_is_refused(self):
        def dirty_git(args, timeout=70):
            if args[0] == "status": return " M local-file.txt"
            return self.git(args, timeout)
        with patch.object(self.branch, "run", side_effect=dirty_git):
            with self.assertRaises(self.branch.SwitchDenied) as raised:
                self.branch.switch("archive/local-only", self.current, self.target,
                                   "github.com/krmobdev/quizapp", local_only=True)
        self.assertEqual(str(raised.exception), "WORKTREE_DIRTY")
        self.assertFalse(any(args[0] == "switch" for args in self.visited))

    def test_verified_branch_must_check_github(self):
        with patch.object(self.branch, "run", side_effect=self.git):
            with self.assertRaises(AssertionError):
                self.branch.switch("archive/local-only", self.current, self.target,
                                   "github.com/krmobdev/quizapp", local_only=False)


if __name__ == "__main__":
    unittest.main(verbosity=2)
