"""Explicit origin fetch must never checkout, reset, force-push or touch user files."""
import pathlib
import tempfile
import types
import unittest
from unittest import mock
import catalog

class CatalogFetchTests(unittest.TestCase):
    def test_clean_registered_checkout_updates_tracking_refs_only(self):
        with tempfile.TemporaryDirectory() as temp:
            home=pathlib.Path(temp)
            root=home/"anima-workspace"/"Microlab"
            root.mkdir(parents=True)
            marker=root/"experiment.txt"
            marker.write_text("keep")
            current={"path":str(root),"error":"","worktree":False,"dirty":False,
                     "remote":"github.com/krmobdev/MicrolabSolo7CRemoteV2"}
            updated=dict(current,branches=[],branches_offset=0,branches_next=0,
                         branches_more=False)
            with mock.patch.object(catalog,"inspect",side_effect=[current,updated]), \
                 mock.patch.object(catalog.subprocess,"run",return_value=types.SimpleNamespace(returncode=0)) as git:
                result=catalog.refresh_origin(str(root),home=home,git="/usr/bin/git")
            self.assertTrue(result["projects"][0]["refs_refreshed"])
            called=git.call_args.args[0]
            self.assertIn("fetch",called)
            self.assertIn("--no-tags",called)
            self.assertIn("--no-write-fetch-head",called)
            for forbidden in ("checkout","switch","reset","clean","push","--force","--prune"):
                self.assertNotIn(forbidden,called)
            self.assertEqual(marker.read_text(),"keep")
    def test_dirty_checkout_cannot_fetch(self):
        with tempfile.TemporaryDirectory() as temp:
            home=pathlib.Path(temp)
            root=home/"anima-workspace"/"Microlab"
            root.mkdir(parents=True)
            current={"path":str(root),"error":"","worktree":False,"dirty":True,
                     "remote":"github.com/krmobdev/MicrolabSolo7CRemoteV2"}
            with mock.patch.object(catalog,"inspect",return_value=current), \
                 mock.patch.object(catalog.subprocess,"run") as git:
                result=catalog.refresh_origin(str(root),home=home,git="/usr/bin/git")
            self.assertNotIn("refs_refreshed",result["projects"][0])
            git.assert_not_called()
    def test_unverified_remote_cannot_fetch(self):
        with tempfile.TemporaryDirectory() as temp:
            home=pathlib.Path(temp)
            root=home/"project"
            root.mkdir()
            current={"path":str(root),"error":"","worktree":False,"dirty":False,
                     "remote":"Remote скрыт"}
            with mock.patch.object(catalog,"inspect",return_value=current), \
                 mock.patch.object(catalog.subprocess,"run") as git:
                result=catalog.refresh_origin(str(root),home=home,git="/usr/bin/git")
            self.assertNotIn("refs_refreshed",result["projects"][0])
            git.assert_not_called()

if __name__=="__main__":
    unittest.main()
