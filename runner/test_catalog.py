import pathlib
import shutil
import subprocess
import tempfile
import unittest
import catalog

class CatalogTests(unittest.TestCase):
    def test_bundled_runner_is_exactly_the_verified_catalog(self):
        source=pathlib.Path(__file__).resolve().parent/"catalog.py"
        bundled=source.parent.parent/"app/src/main/assets/catalog/catalog.py"
        self.assertEqual(source.read_bytes(),bundled.read_bytes(),
                         "The APK catalog asset is out of sync with tested runner/catalog.py")

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
    def test_local_without_origin_reports_empty_remote_even_if_dirty(self):
        self.run_git('remote','remove','origin')
        (self.repo/'work-in-progress').write_text('local edits')
        item=catalog.inspect(self.repo,self.home,self.git)
        self.assertEqual(item['remote'],'')
        self.assertTrue(item['dirty'])
        self.assertEqual(item['branch'],'test/decomposition-v1')
        self.assertEqual(item['error'],'')
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

    def test_common_directory_groups_linked_worktree(self):
        linked=self.home/'linked';self.run_git('worktree','add','-b','linked',str(linked))
        normal=catalog.inspect(self.repo,self.home,self.git);worktree=catalog.inspect(linked,self.home,self.git)
        self.assertEqual(normal['repository_path'],str(self.repo));self.assertEqual(worktree['repository_path'],normal['repository_path'])
    def test_copy_has_distinct_repository_identity(self):
        copy=self.home/'copy';self.make_nested(copy)
        self.assertNotEqual(catalog.inspect(copy,self.home,self.git)['repository_path'],catalog.inspect(self.repo,self.home,self.git)['repository_path'])
    def test_default_branch_from_origin_head_not_name_guess(self):
        self.run_git('update-ref','refs/remotes/origin/trunk','HEAD')
        self.run_git('symbolic-ref','refs/remotes/origin/HEAD','refs/remotes/origin/trunk')
        r=catalog.inspect(self.repo,self.home,self.git,branches=True)
        self.assertEqual(r['default_branch'],'trunk');self.assertTrue(r['branches'][0]['default']);self.assertEqual(r['branches'][0]['name'],'trunk')
        self.assertTrue(any(b['active'] for b in r['branches']));self.assertTrue(all(b['date'] and len(b['commit'])==40 for b in r['branches']))
    def test_absent_remote_head_is_unknown(self):
        self.run_git('branch','main')
        self.assertEqual(catalog.inspect(self.repo,self.home,self.git,branches=True)['default_branch'],'')
        self.run_git('symbolic-ref','refs/remotes/origin/HEAD','refs/remotes/origin/missing')
        self.assertEqual(catalog.inspect(self.repo,self.home,self.git,branches=True)['default_branch'],'')
    def test_branch_view_does_not_checkout_or_mutate(self):
        (self.repo/'local').write_text('preserve')
        branch=self.run_git('branch','--show-current');sha=self.run_git('rev-parse','HEAD')
        r=catalog.explicit(str(self.repo),self.home,self.git,branches=True)
        self.assertTrue(r['projects'][0]['dirty']);self.assertEqual(branch,self.run_git('branch','--show-current'));self.assertEqual(sha,self.run_git('rev-parse','HEAD'));self.assertEqual('preserve',(self.repo/'local').read_text())
    def test_all_branches_are_accessible_through_pages(self):
        for i in range(45):self.run_git('branch','fixture'+str(i))
        r=catalog.inspect(self.repo,self.home,self.git,branches=True)
        self.assertTrue(r['branches_more']);self.assertFalse(r['branches_limited']);self.assertTrue(any(b['active'] for b in r['branches']))
        seen={(b['name'],b['local']) for b in r['branches']}
        while r['branches_more']:
            r=catalog.inspect(self.repo,self.home,self.git,branches=True,branch_offset=r['branches_next'])
            seen.update((b['name'],b['local']) for b in r['branches'])
        self.assertEqual(len(seen),46)


    def test_negative_branch_offset_is_rejected(self):
        r=catalog.inspect(self.repo,self.home,self.git,branches=True,branch_offset=-1)
        self.assertTrue(r['error'])
    def test_remote_and_local_refs_survive_paging(self):
        for i in range(70):
            self.run_git('branch','feature/'+str(i))
            self.run_git('update-ref','refs/remotes/origin/feature/'+str(i),'HEAD')
        offset=0;seen=set()
        while True:
            r=catalog.inspect(self.repo,self.home,self.git,branches=True,branch_offset=offset)
            self.assertFalse(r['error']);self.assertLessEqual(len(r['branches']),2*catalog.BRANCH_PAGE_SIZE+2)  # paired local/origin counterparts
            seen.update((b['name'],b['local']) for b in r['branches'])
            if not r['branches_more']:break
            self.assertGreater(r['branches_next'],offset);offset=r['branches_next']
        self.assertEqual(len(seen),141)
