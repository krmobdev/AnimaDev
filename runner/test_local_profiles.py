"""Fixture-only tests: offline local Git projects never fetch or sync."""
import hashlib
import unittest
import uuid
from unittest.mock import patch
import animadev_runner as runner
import test_runner as base

class LocalProfiles(unittest.TestCase):
    def setUp(self):
        base.Fixture.setUp(self)
        base.git(self.repo,'remote','remove','origin')
    tearDown=base.Fixture.tearDown

    def profile(self):
        return dict(id=hashlib.sha256(str(self.repo).encode()).hexdigest()[:32],
                    name="FlyLifeLocal",root=str(self.repo),remote="",branch=base.BRANCH)

    def test_dirty_local_repo_check_requires_no_network(self):
        with patch.object(runner,"HOME",self.base), patch.object(runner.GitGuard,"fetch",
                side_effect=AssertionError("local mode must never fetch")):
            profile=self.profile()
            engine=runner.Engine(self.base/"local-state",{})
            engine.register_profile(profile["id"],profile)
            (self.repo/"uncommitted.txt").write_text("work in progress")
            jid=uuid.uuid4().hex
            engine.start(jid,profile["id"],"CHECK",spawn=False)
            engine.run_job(jid)
            result=engine.query(jid,profile["id"],"CHECK")
            self.assertEqual(result["state"],"SUCCEEDED",result)
            self.assertTrue(result["snapshot"]["local_only"])
            self.assertTrue(result["snapshot"]["checked"])
            self.assertTrue(result["snapshot"]["dirty"])
            self.assertFalse(result["snapshot"]["remote_ok"])
            self.assertEqual(result["snapshot"]["local_commit"],self.before)

    def test_hidden_origin_is_not_trusted_as_local(self):
        with patch.object(runner,"HOME",self.base):
            profile=self.profile()
            engine=runner.Engine(self.base/"local-state",{})
            engine.register_profile(profile["id"],profile)
            base.git(self.repo,"remote","add","origin","https://untrusted.invalid/repo")
            jid=uuid.uuid4().hex
            engine.start(jid,profile["id"],"CHECK",spawn=False)
            engine.run_job(jid)
            result=engine.query(jid,profile["id"],"CHECK")
            self.assertEqual(result["state"],"FAILED",result)
            self.assertEqual(result["error_code"],"REMOTE_MISMATCH")

    def test_local_sync_is_never_permitted(self):
        with patch.object(runner,"HOME",self.base):
            profile=self.profile()
            engine=runner.Engine(self.base/"local-state",{})
            engine.register_profile(profile["id"],profile)
            jid=uuid.uuid4().hex
            engine.start(jid,profile["id"],"SYNC",spawn=False)
            engine.run_job(jid)
            result=engine.query(jid,profile["id"],"SYNC")
            self.assertEqual(result["state"],"FAILED")
            self.assertEqual(result["error_code"],"ACTION_DENIED")

if __name__=="__main__":unittest.main()
