"""Reproduce Android's stuck FlyLife CHECK without touching any user jobs."""
import contextlib
import hashlib
import io
import json
import sys
import unittest
import uuid
from unittest.mock import patch

import animadev_runner as runner
import test_runner as base

class OrphanQuery(unittest.TestCase):
    tearDown=base.Fixture.tearDown
    def setUp(self):
        base.Fixture.setUp(self)
        base.git(self.repo,"remote","remove","origin")
        self.local_profile={
            "id":hashlib.sha256(str(self.repo).encode()).hexdigest()[:32],
            "name":"FlyLife","root":str(self.repo),
            "remote":"","branch":base.BRANCH
        }

    def reply(self,engine,jid,profile=None):
        payload={"request_id":uuid.uuid4().hex,"mode":"QUERY",
            "job_id":jid,"project_id":self.local_profile["id"],"action":"CHECK"}
        if profile is not False:
            payload["profile"]=self.local_profile if profile is None else profile
        stdout=io.StringIO()
        # As in Android, QUERY must never become another START.
        with patch.object(runner,"HOME",self.base),patch.object(runner,"Engine",return_value=engine),\
             patch.object(engine,"start",side_effect=AssertionError("QUERY replayed START")),\
             patch.object(sys,"argv",["animadev_runner.py",json.dumps(payload)]),\
             contextlib.redirect_stdout(stdout):
            code=runner.main()
        return code,json.loads(stdout.getvalue())

    def test_orphan_gets_tombstone_and_unblocks_not_start(self):
        engine=runner.Engine(self.base/"state2",{})
        jid=uuid.uuid4().hex
        old_sha=base.git(self.repo,"rev-parse","HEAD")
        code,reply=self.reply(engine,jid)
        self.assertEqual(code,0,reply)
        self.assertTrue(reply["ok"],reply)
        self.assertEqual(reply["job"]["state"],"NOT_STARTED")
        self.assertEqual(reply["job"]["job_id"],jid)
        self.assertEqual(reply["runner_version"],runner.VERSION)
        self.assertEqual(base.git(self.repo,"rev-parse","HEAD"),old_sha)
        code,again=self.reply(engine,jid)
        self.assertEqual(code,0)
        self.assertEqual(again["job"]["state"],"NOT_STARTED")

    def test_previously_completed_check_is_retrieved_unmodified(self):
        engine=runner.Engine(self.base/"state2",{})
        profile=self.local_profile
        with patch.object(runner,"HOME",self.base):
            engine.register_profile(profile["id"],profile)
            jid=uuid.uuid4().hex
            engine.start(jid,profile["id"],"CHECK",spawn=False)
            engine.run_job(jid)
            old=engine.query(jid,profile["id"],"CHECK")
        self.assertEqual(old["state"],"SUCCEEDED",old)
        fresh=runner.Engine(self.base/"state2",{})
        code,reply=self.reply(fresh,jid)
        self.assertEqual(code,0,reply)
        self.assertTrue(reply["ok"],reply)
        self.assertEqual(reply["job"]["state"],"SUCCEEDED")
        self.assertEqual(reply["job"]["snapshot"]["local_commit"],self.before)

    def test_unregistered_query_without_profile_does_not_guess(self):
        engine=runner.Engine(self.base/"state2",{})
        code,reply=self.reply(engine,uuid.uuid4().hex,profile=False)
        self.assertEqual(code,0)
        self.assertFalse(reply["ok"])
        self.assertEqual(reply["error_code"],"PROJECT_NOT_FOUND")

    def test_invalid_profile_never_reconciles(self):
        engine=runner.Engine(self.base/"state2",{})
        malformed=dict(self.local_profile,id="0"*32)
        code,reply=self.reply(engine,uuid.uuid4().hex,profile=malformed)
        self.assertEqual(code,0)
        self.assertFalse(reply["ok"])
        self.assertEqual(reply["error_code"],"PROJECT_NOT_FOUND")

if __name__=="__main__":unittest.main()
