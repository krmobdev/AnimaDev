import pathlib
import shutil
import subprocess
import tempfile
import unittest
import catalog

class CatalogTests(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory();self.addCleanup(self.tmp.cleanup)
        self.home=pathlib.Path(self.tmp.name)
        self.repo=self.home/'quizapp';self.repo.mkdir()
        self.git=shutil.which('git')
        self.run_git('init','-b','test/decomposition-v1')
        self.run_git('-c','user.name=fixture','-c','user.email=fixture@example.invalid','commit','--allow-empty','-m','fixture')
        self.run_git('remote','add','origin','git@github.com:krmobdev/quizapp.git')
    def run_git(self,*args):return subprocess.check_output([self.git,*args],cwd=self.repo,stderr=subprocess.DEVNULL,text=True).strip()
    def test_real_branch_metadata(self):
        r=catalog.inspect(self.repo,self.home,self.git)
        self.assertEqual(r['id'],'animaquiz');self.assertEqual(r['branch'],'test/decomposition-v1');self.assertFalse(r['dirty']);self.assertEqual(r['remote'],'github.com/krmobdev/quizapp')
    def test_dirty_is_reported_without_mutation(self):
        (self.repo/'user-file').write_text('local change')
        sha=self.run_git('rev-parse','HEAD');r=catalog.inspect(self.repo,self.home,self.git)
        self.assertTrue(r['dirty']);self.assertEqual((self.repo/'user-file').read_text(),'local change');self.assertEqual(sha,self.run_git('rev-parse','HEAD'))
    def test_project_symlink_not_followed(self):
        (self.home/'alias').symlink_to(self.repo,target_is_directory=True)
        self.assertFalse(catalog.permitted(self.home/'alias',self.home));self.assertNotIn(self.home/'alias',catalog.candidates(self.home))
    def test_git_marker_symlink_denied(self):
        other=self.home/'other';other.mkdir();(other/'.git').symlink_to(self.repo/'.git',target_is_directory=True)
        self.assertIsNone(catalog.inspect(other,self.home,self.git))
    def test_hidden_protected_root_excluded(self):
        private=self.home/'.ssh';private.mkdir();self.assertFalse(catalog.permitted(private,self.home));self.assertNotIn(private,catalog.candidates(self.home))
    def test_storage_sdk_excluded(self):
        for name in ('storage','android-sdk','node_modules'):
            p=self.home/name;p.mkdir();self.assertFalse(catalog.permitted(p,self.home));self.assertNotIn(p,catalog.candidates(self.home))
    def test_allowed_workspace_child(self):
        workspace=self.home/'anima-workspace';workspace.mkdir();child=workspace/'AnimaDev';child.mkdir()
        self.assertTrue(catalog.permitted(child,self.home));self.assertIn(child,catalog.candidates(self.home))
    def test_arbitrary_deep_root_denied(self):
        self.assertFalse(catalog.permitted(self.home/'other'/'nested',self.home));self.assertFalse(catalog.permitted(self.home/'anima-workspace'/'a'/'nested',self.home))
    def test_outside_root_denied(self):self.assertFalse(catalog.permitted(self.home.parent/'outside',self.home))
    def test_no_git_no_project(self):
        folder=self.home/'empty';folder.mkdir();self.assertIsNone(catalog.inspect(folder,self.home,self.git))
    def test_includes_denied_without_execution(self):
        self.run_git('config','include.path','/unavailable')
        r=catalog.inspect(self.repo,self.home,self.git);self.assertTrue(r['error']);self.assertEqual(r['branch'],'')
    def test_filter_configuration_denied(self):
        self.run_git('config','filter.demo.clean','not-executed')
        self.assertTrue(catalog.inspect(self.repo,self.home,self.git)['error'])
    def test_remote_nonstandard_not_persisted(self):
        self.run_git('remote','set-url','origin','not-a-standard-remote')
        r=catalog.inspect(self.repo,self.home,self.git);self.assertNotIn('not-a-standard-remote',r['remote']);self.assertIn('скрыт',r['remote'])
    def test_standard_https_remote(self):self.assertEqual(catalog.remote_label('https://github.com/krmobdev/quizapp.git'),'github.com/krmobdev/quizapp')
    def test_catalog_returns_own_project_only(self):
        r=catalog.catalog(self.home,self.git);self.assertEqual(len(r['projects']),1);self.assertEqual(r['projects'][0]['path'],str(self.repo))
    def test_git_file_worktree_not_added_as_supported(self):
        other=self.home/'worktree';other.mkdir();(other/'.git').write_text('gitdir: /not/allowed')
        self.assertIsNone(catalog.inspect(other,self.home,self.git))
