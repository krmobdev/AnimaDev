"""Synthetic Release Ctrl+C request tests. Never invokes Gradle or a real signing key."""
import contextlib
import importlib.util
import io
import os
import pathlib
import sys
import tempfile
import unittest
from unittest import mock
import json

ROOT=pathlib.Path(__file__).resolve().parent
SPEC=importlib.util.spec_from_file_location("release_cancel_fixture",ROOT/"release_cancel.py")
MODULE=importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)

class ReleaseCancelTests(unittest.TestCase):
    def request(self,state,jid):
        out=io.StringIO()
        with mock.patch.object(MODULE,"STATE",state),mock.patch.object(sys,"argv",["-c",jid]),contextlib.redirect_stdout(out):
            MODULE.main()
        return json.loads(out.getvalue())
    def test_active_job_requests_ctrl_c_without_secrets(self):
        with tempfile.TemporaryDirectory() as folder:
            state=pathlib.Path(folder);jid="a"*32
            (state/(".release-active-"+jid)).touch()
            result=self.request(state,jid)
            self.assertEqual(result,{"request_id":jid,"ok":True,"code":"CANCEL_REQUESTED"})
            self.assertTrue((state/(".release-cancel-"+jid)).is_file())
            self.assertEqual((state/(".release-cancel-"+jid)).stat().st_size,0)
            self.assertEqual(self.request(state,jid)["code"],"CANCEL_REQUESTED")
    def test_missing_job_never_creates_marker(self):
        with tempfile.TemporaryDirectory() as folder:
            state=pathlib.Path(folder);jid="b"*32
            self.assertEqual(self.request(state,jid)["code"],"JOB_NOT_ACTIVE")
            self.assertFalse((state/(".release-cancel-"+jid)).exists())
    def test_path_traversal_rejected(self):
        with tempfile.TemporaryDirectory() as folder:
            state=pathlib.Path(folder);jid="../"+("a"*32)
            self.assertFalse(self.request(state,jid)["ok"])
            self.assertFalse(list(state.glob(".release-cancel-*")))
    def test_symlinked_active_marker_refused(self):
        with tempfile.TemporaryDirectory() as folder:
            state=pathlib.Path(folder);jid="c"*32
            target=state/"elsewhere";target.touch()
            (state/(".release-active-"+jid)).symlink_to(target)
            self.assertEqual(self.request(state,jid)["code"],"JOB_NOT_ACTIVE")
            self.assertFalse((state/(".release-cancel-"+jid)).exists())
if __name__=="__main__":unittest.main()
