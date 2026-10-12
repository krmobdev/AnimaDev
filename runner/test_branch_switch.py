"""Branch switching must fail closed without modifying user Git work."""
import importlib.util
import pathlib
import tempfile
import unittest
from unittest import mock

FILE=pathlib.Path(__file__).resolve().parent.parent/"app/src/main/assets/catalog/branch_switch.py"
SPEC=importlib.util.spec_from_file_location("animadev_branch_switch",FILE)
module=importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(module)
CURRENT="a"*40
TARGET="b"*40

class BranchSwitchTests(unittest.TestCase):
    def test_rejects_options_path_traversal_and_shell_metacharacters(self):
        for name in ("-main","../main","test//main","main.lock","foo;rm", "a b","@{HEAD}","foo/.hidden"):
            self.assertFalse(module.valid_branch(name),name)
        for name in ("main","test/decomposition-v1","release/3.7.21"):
            self.assertTrue(module.valid_branch(name),name)

    def test_additional_registered_repository_is_allowed_but_symlink_is_not(self):
        with tempfile.TemporaryDirectory() as tmp:
            home=pathlib.Path(tmp)
            root=home/"anima-workspace"/"MicrolabSolo7CRemoteV2"
            root.mkdir(parents=True)
            (root/".git").mkdir()
            with mock.patch.object(module,"HOME",home):
                self.assertEqual(module.validate_project(str(root),
                    "github.com/krmobdev/MicrolabSolo7CRemoteV2"),root)
                with self.assertRaises(module.SwitchDenied):
                    module.validate_project(str(root),"example.com/other/Microlab")
                linked=home/"anima-workspace"/"link"
                linked.symlink_to(root,target_is_directory=True)
                with self.assertRaises(module.SwitchDenied):
                    module.validate_project(str(linked),"github.com/krmobdev/MicrolabSolo7CRemoteV2")

    def test_dirty_worktree_cannot_switch(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=pathlib.Path(tmp)/"quizapp";root.mkdir();(root/".git").mkdir()
            commands=[]
            def git(argv,timeout=70):
                commands.append(argv)
                if argv==["rev-parse","--show-toplevel"]:return str(root)
                if argv==["remote","get-url","origin"]:return "git@github.com:krmobdev/quizapp.git"
                if argv==["symbolic-ref","--quiet","--short","HEAD"]:return "test/decomposition-v1"
                if argv==["rev-parse","HEAD"]:return CURRENT
                if argv==["status","--porcelain=v1","--untracked-files=all"]:return " M app/src/main/AndroidManifest.xml"
                raise AssertionError("Unexpected command")
            with mock.patch.object(module,"ROOT",root),mock.patch.object(module,"run",side_effect=git):
                with self.assertRaises(module.SwitchDenied) as failure:
                    module.switch("main",CURRENT,TARGET)
            self.assertEqual(str(failure.exception),"WORKTREE_DIRTY")
            self.assertFalse(any(x and x[0]=="switch" for x in commands))

    def test_differing_remote_sha_prevents_switch(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=pathlib.Path(tmp)/"quizapp";root.mkdir();(root/".git").mkdir()
            commands=[]
            def git(argv,timeout=70):
                commands.append(argv)
                if argv==["rev-parse","--show-toplevel"]:return str(root)
                if argv==["remote","get-url","origin"]:return "git@github.com:krmobdev/quizapp.git"
                if argv==["symbolic-ref","--quiet","--short","HEAD"]:return "test/decomposition-v1"
                if argv==["rev-parse","HEAD"]:return CURRENT
                if argv==["status","--porcelain=v1","--untracked-files=all"]:return ""
                if argv==["show-ref","--verify","refs/heads/main"]:return TARGET+"\trefs/heads/main"
                if argv==["rev-parse","refs/heads/main"]:return TARGET
                if argv[:2]==["ls-remote","--heads"]:return CURRENT+"\trefs/heads/main"
                raise AssertionError("Unexpected command")
            with mock.patch.object(module,"ROOT",root),mock.patch.object(module,"run",side_effect=git):
                with self.assertRaises(module.SwitchDenied) as failure:
                    module.switch("main",CURRENT,TARGET)
            self.assertEqual(str(failure.exception),"GITHUB_SHA_MISMATCH")
            self.assertFalse(any(x and x[0]=="switch" for x in commands))

if __name__=="__main__":unittest.main()
