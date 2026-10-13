"""Offline safety regressions. All Git operations are mocked, history uses a temp DB."""
import importlib.util
import pathlib
import tempfile
import unittest
import uuid
from unittest.mock import patch

BASE=pathlib.Path(__file__).resolve().parents[1]
def load(name,file):
    spec=importlib.util.spec_from_file_location(name,BASE/file)
    m=importlib.util.module_from_spec(spec)
    spec.loader.exec_module(m)
    return m

class HistoryTests(unittest.TestCase):
    def test_existing_100_jobs_are_preserved(self):
        r=load("anima_jobs","app/src/main/assets/runner/animadev_runner.py")
        with tempfile.TemporaryDirectory() as directory:
            e=r.Engine(root=pathlib.Path(directory))
            jobs=[uuid.uuid4().hex for _ in range(105)]
            for j in jobs:self.assertEqual(e.query(j,"animaquiz","CHECK")["state"],"NOT_STARTED")
            with e.store.transaction() as c:
                self.assertEqual(c.execute("SELECT COUNT(*) FROM jobs").fetchone()[0],105)
                self.assertEqual(e.store.get(c,jobs[0])["job_id"],jobs[0])

class GithubFirstTests(unittest.TestCase):
    def setUp(self):
        self.m=load("branch_switch_test","app/src/main/assets/catalog/branch_switch.py")
        self.temp=tempfile.TemporaryDirectory();self.addCleanup(self.temp.cleanup)
        self.root=pathlib.Path(self.temp.name);(self.root/".git").mkdir()
        self.m.ROOT=self.root
        self.original="a"*40;self.sha="c"*40;self.local=""
        self.current="main";self.head=self.original
        self.dirty=False;self.diverged=False;self.tracking=self.sha;self.github=self.sha
        self.visited=[]
    def git(self,args,timeout=70):
        self.visited.append(args)
        if args==["rev-parse","--show-toplevel"]:return str(self.root)
        if args==["remote","get-url","origin"]:return "git@github.com:krmobdev/quizapp.git"
        if args==["symbolic-ref","--quiet","--short","HEAD"]:return self.current
        if args==["rev-parse","HEAD"]:return self.head
        if args[0]=="status":return " M test.txt" if self.dirty else ""
        if args==["rev-parse","--verify","refs/remotes/origin/feature/new"]:return self.tracking
        if args==["ls-remote","--heads","origin","refs/heads/feature/new"]:
            return self.github+"\trefs/heads/feature/new"
        if args==["for-each-ref","--format=%(refname)%09%(objectname)","refs/heads/feature/new"]:
            return "refs/heads/feature/new\t"+self.local if self.local else ""
        if args[0]=="merge-base":
            if self.diverged:raise self.m.SwitchDenied("GIT_REJECTED")
            return ""
        if args==["switch","--no-guess","--track","-c","feature/new","origin/feature/new"]:
            self.local=self.sha;self.head=self.sha;self.current="feature/new";return ""
        if args==["switch","--no-guess","feature/new"]:
            self.head=self.local;self.current="feature/new";return ""
        if args==["merge","--ff-only","refs/remotes/origin/feature/new"]:
            self.local=self.sha;self.head=self.sha;return ""
        raise AssertionError(str(args))
    def switch(self):
        with patch.object(self.m,"run",side_effect=self.git):
            return self.m.switch("feature/new",self.original,self.sha,"github.com/krmobdev/quizapp")
    def test_remote_only_creates_tracking(self):
        self.assertEqual(self.switch()["commit"],self.sha)
        self.assertTrue(any(a[:3]==["switch","--no-guess","--track"] for a in self.visited))
    def test_local_behind_fast_forward_only(self):
        self.local="b"*40
        self.assertEqual(self.switch()["commit"],self.sha)
        self.assertIn(["merge","--ff-only","refs/remotes/origin/feature/new"],self.visited)
    def test_diverged_local_is_not_modified(self):
        self.local="b"*40;self.diverged=True
        with self.assertRaises(self.m.SwitchDenied) as e:self.switch()
        self.assertEqual(str(e.exception),"LOCAL_DIVERGED")
        self.assertEqual(self.current,"main")
    def test_dirty_is_denied(self):
        self.dirty=True
        with self.assertRaises(self.m.SwitchDenied) as e:self.switch()
        self.assertEqual(str(e.exception),"WORKTREE_DIRTY")
        self.assertFalse(any(a[0]=="switch" for a in self.visited))
    def test_outdated_origin_is_denied(self):
        self.tracking="d"*40
        with self.assertRaises(self.m.SwitchDenied) as e:self.switch()
        self.assertEqual(str(e.exception),"ORIGIN_STALE")
    def test_github_sha_change_is_denied(self):
        self.github="d"*40
        with self.assertRaises(self.m.SwitchDenied) as e:self.switch()
        self.assertEqual(str(e.exception),"GITHUB_SHA_MISMATCH")

class CatalogPageTests(unittest.TestCase):
    def test_matching_origin_is_in_same_page_as_local_ref(self):
        c=load("catalog_pair_test","app/src/main/assets/catalog/catalog.py")
        target="archive/millionaire-premium-light-ui-2026-10-07"
        local="refs/heads/"+target+"\t"+"a"*40+"\t2026-10-07T10:00:00+00:00"
        remote="refs/remotes/origin/"+target+"\t"+"a"*40+"\t2026-10-07T10:00:00+00:00"
        calls=[]
        def git(args):
            calls.append(args)
            return 0,remote
        result=c.paired_ref_rows([local],git)
        self.assertEqual(result,[local,remote])
        self.assertEqual(len(calls),1)

    def test_missing_origin_does_not_invent_remote_sha(self):
        c=load("catalog_pair_test_missing","app/src/main/assets/catalog/catalog.py")
        local="refs/heads/local-only\t"+"a"*40+"\t2026-10-07T10:00:00+00:00"
        self.assertEqual(c.paired_ref_rows([local],lambda _: (0,"")),[local])


if __name__=="__main__":unittest.main()
