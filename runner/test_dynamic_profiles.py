"""Regression: only catalog-matching local GitHub profiles can execute fixed actions."""
import hashlib
import pathlib
import unittest
import uuid
from unittest.mock import patch
import animadev_runner as runner
import test_runner as base

class DynamicProfiles(unittest.TestCase):
    setUp = base.Fixture.setUp
    tearDown = base.Fixture.tearDown

    def profile(self):
        return dict(id=hashlib.sha256(str(self.repo).encode()).hexdigest()[:32],
            name="SampleProject",root=str(self.repo),
            remote="github.com/krmobdev/AndroidTest",branch=base.BRANCH)

    def test_fixture_project_can_run_status_and_restore_after_restart(self):
        # STATUS is network-free. No access to a real user Git tree or GitHub.
        base.git(self.repo,'remote','set-url','origin','git@github.com:krmobdev/AndroidTest.git')
        profile=self.profile()
        with patch.object(runner,'HOME',self.base):
            engine=runner.Engine(self.base/'state',{})
            engine.register_profile(profile['id'],profile)
            jid=uuid.uuid4().hex
            engine.start(jid,profile['id'],'STATUS',spawn=False)
            engine.run_job(jid)
            job=engine.query(jid,profile['id'],'STATUS')
            self.assertEqual(job['state'],'SUCCEEDED',job)
            self.assertTrue(job['snapshot']['remote_ok'])
            self.assertTrue(job['snapshot']['branch_allowed'])
            self.assertEqual(job['snapshot']['local_commit'],self.before)
            self.assertEqual(job['profile'],profile)
            reopened=runner.Engine(self.base/'state',{})
            restored=reopened.query(jid,profile['id'],'STATUS')
            self.assertEqual(restored['job_id'],jid)
            self.assertEqual(restored['state'],'SUCCEEDED')
            self.assertEqual(reopened.registry[profile['id']]['root'],str(self.repo))
            self.assertNotIn(profile['id'],runner.REGISTRY)

    def test_identity_and_origin_are_not_user_overrides(self):
        with patch.object(runner,'HOME',self.base):
            engine=runner.Engine(self.base/'state',{})
            for change in ({'id':'a'*32}, {'remote':'https://evil.invalid/repo'},
                           {'branch':'../../main'}, {'branch':'branch.lock'},
                           {'root':'/tmp/external'}):
                data=dict(self.profile(),**change)
                with self.assertRaises(runner.Denied):
                    engine.register_profile(data['id'],data)
            self.assertNotIn(self.profile()['id'],engine.registry)

    def test_existing_quiz_profile_cannot_be_replaced(self):
        with patch.object(runner,'HOME',self.base):
            engine=runner.Engine(self.base/'state',{})
            with self.assertRaises(runner.Denied):
                engine.register_profile('animaquiz',self.profile())

if __name__=='__main__': unittest.main()
