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
        workspace=self.home/'anima-workspace';workspace.mkdir();child=workspace/'AnimaDev';self.make_nested(child)
        self.assertTrue(catalog.permitted(child,self.home));self.assertIn(child,catalog.candidates(self.home))
    def test_nested_paths_allowed(self):
        self.assertTrue(catalog.permitted(self.home/'other'/'nested',self.home));self.assertTrue(catalog.permitted(self.home/'anima-workspace'/'a'/'nested',self.home))
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

    def make_nested(self,path):
        path.mkdir(parents=True)
        subprocess.run([self.git,'init','-b','fixture'],cwd=path,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,check=True)
        subprocess.run([self.git,'-c','user.name=fixture','-c','user.email=fixture@example.invalid','commit','--allow-empty','-m','fixture'],cwd=path,stdout=subprocess.DEVNULL,check=True)
        return path
    def test_nested_repository_found(self):
        p=self.make_nested(self.home/'projects'/'android'/'nested')
        self.assertIn(str(p),[r['path'] for r in catalog.catalog(self.home,self.git)['projects']])
    def test_depth_boundary(self):
        p=self.make_nested(self.home.joinpath(*['p'+str(i) for i in range(6)]))
        self.assertTrue(catalog.permitted(p,self.home));self.assertIn(p,catalog.candidates(self.home))
        self.assertFalse(catalog.permitted(p/'deeper',self.home))
    def test_depth_omission_reported(self):
        p=self.home.joinpath(*['a'+str(i) for i in range(7)]);p.mkdir(parents=True)
        r=catalog.catalog(self.home,self.git);self.assertTrue(r['limited']);self.assertTrue(r['issues'])
    def test_valid_worktree_read_only_metadata(self):
        p=self.home/'projects'/'linked';p.parent.mkdir()
        self.run_git('worktree','add','-b','linked',str(p))
        r=catalog.inspect(p,self.home,self.git)
        self.assertTrue(r['worktree']);self.assertEqual(r['branch'],'linked');self.assertEqual(r['error'],'');self.assertNotEqual(r['id'],'animaquiz')
    def test_worktree_external_common_directory_denied(self):
        p=self.home/'linked';p.mkdir();(p/'.git').write_text('gitdir: /outside/.git/worktrees/linked')
        self.assertIsNone(catalog.inspect(p,self.home,self.git))
    def test_worktree_backlink_mismatch_denied(self):
        p=self.home/'linked';self.run_git('worktree','add','-b','linked',str(p))
        actual=pathlib.Path((p/'.git').read_text().strip()[8:]);(actual/'gitdir').write_text('/different/.git')
        self.assertIsNone(catalog.inspect(p,self.home,self.git))
    def test_worktree_filter_config_denied(self):
        p=self.home/'linked';self.run_git('worktree','add','-b','linked',str(p))
        self.run_git('config','extensions.worktreeConfig','true')
        subprocess.run([self.git,'-C',str(p),'config','--worktree','filter.fixture.smudge','do-not-run'],check=True)
        self.assertTrue(catalog.inspect(p,self.home,self.git)['error'])
    def test_explicit_nested_path(self):
        p=self.make_nested(self.home/'projects'/'nested')
        self.assertEqual(catalog.explicit(str(p),self.home,self.git)['projects'][0]['path'],str(p))
    def test_explicit_denial_has_reason(self):
        r=catalog.explicit(str(self.home/'.ssh'),self.home,self.git)
        self.assertEqual(r['projects'],[]);self.assertTrue(r['issues'])
    def test_symlink_omission_reported(self):
        (self.home/'alias').symlink_to(self.repo,target_is_directory=True)
        r=catalog.catalog(self.home,self.git);self.assertTrue(any('alias' in i['path'] for i in r['issues']))
    def test_service_nested_directory_excluded(self):
        self.assertFalse(catalog.permitted(self.home/'projects'/'node_modules'/'repo',self.home))
    def test_android_python_policy_agree(self):
        import re
        source=(pathlib.Path(__file__).parent.parent/'app/src/main/java/com/animacode/animadev/projects/ProjectPolicy.kt').read_text()
        values=set(re.findall(r'"([A-Za-z_\-]+)"',source.split('private val excluded=setOf(')[1].split(')')[0]))
        self.assertEqual(values,catalog.DENIED);self.assertIn('parts.size in 1..'+str(catalog.MAX_DEPTH),source)

    def test_directory_count_limit_reported(self):
        from unittest.mock import patch
        (self.home/'projects'/'nested').mkdir(parents=True)
        with patch.object(catalog,'MAX_DIRS',1):
            r=catalog.catalog(self.home,self.git)
        self.assertTrue(r['limited']);self.assertTrue(r['issues'])
    def test_invalid_gitfile_does_not_break_catalog(self):
        p=self.home/'invalid';p.mkdir();(p/'.git').write_bytes(bytes([255,254,128]))
        r=catalog.catalog(self.home,self.git);self.assertEqual(len(r['projects']),1);self.assertTrue(r['issues'])
    def test_worktree_symlink_control_file_denied(self):
        p=self.home/'linked';self.run_git('worktree','add','-b','linked',str(p))
        target=pathlib.Path((p/'.git').read_text().strip()[8:]);common=target/'commondir';common.unlink();common.symlink_to(target/'HEAD')
        self.assertIsNone(catalog.inspect(p,self.home,self.git))

    def test_repository_ssh_command_config_denied(self):
        self.run_git('config','core.sshCommand','do-not-run')
        self.assertTrue(catalog.inspect(self.repo,self.home,self.git)['error'])
    def test_partial_clone_config_denied(self):
        self.run_git('config','remote.origin.promisor','true')
        self.assertTrue(catalog.inspect(self.repo,self.home,self.git)['error'])
    def test_fsmonitor_hook_not_run(self):
        marker=self.home/'hook-ran';hook=self.home/'fixture-hook'
        hook.write_text('#!/bin/sh\ntouch '+str(marker)+'\n');hook.chmod(0o700)
        self.run_git('config','core.fsmonitor',str(hook))
        r=catalog.inspect(self.repo,self.home,self.git)
        self.assertEqual(r['error'],'');self.assertFalse(marker.exists())
