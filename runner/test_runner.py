import json
import os
import pathlib
import subprocess
import shutil
import concurrent.futures
import multiprocessing
import tempfile
import time
import unittest
import uuid
from unittest.mock import patch
from animadev_runner import Engine, GitGuard, Denied, redact, identity, wire_job, classify_fetch_error, MESSAGES

BRANCH='test/decomposition-v1'
def git(root,*args):
    p=subprocess.run(['git','-c','user.name=Fixture','-c','user.email=fixture@example.invalid',*args],cwd=root,capture_output=True,text=True)
    if p.returncode: raise AssertionError(p.stderr)
    return p.stdout.strip()

class Fixture(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory(prefix='animadev-fixture-')
        self.base=pathlib.Path(self.tmp.name)
        self.remote=self.base/'remote.git'; self.seed=self.base/'seed'; self.repo=self.base/'repo'
        self.seed.mkdir(); git(self.seed,'init','-b',BRANCH)
        (self.seed/'file.txt').write_text('one\n'); git(self.seed,'add','.'); git(self.seed,'commit','-m','one')
        git(self.base,'init','--bare',str(self.remote))
        git(self.seed,'remote','add','origin',str(self.remote)); git(self.seed,'push','origin',BRANCH)
        git(self.base,'clone','--branch',BRANCH,str(self.remote),str(self.repo))
        self.project={'name':'Fixture','root':str(self.repo),'origin':str(self.remote),'branch':BRANCH}
        self.engine=Engine(self.base/'state',{'animaquiz':self.project})
        self.before=git(self.repo,'rev-parse','HEAD')
    def tearDown(self): self.tmp.cleanup()
    def remote_commit(self):
        (self.seed/'file.txt').write_text('two\n'); git(self.seed,'add','.'); git(self.seed,'commit','-m','two'); git(self.seed,'push','origin',BRANCH)
        return git(self.seed,'rev-parse','HEAD')
    def run_action(self,action):
        jid=uuid.uuid4().hex
        self.engine.start(jid,'animaquiz',action,spawn=False)
        self.engine.run_job(jid)
        return self.engine.query(jid,'animaquiz',action)
    def assert_blocked(self,code,action='SYNC'):
        j=self.run_action(action);self.assertEqual(j['error_code'],code);self.assertNotEqual(j['state'],'SUCCEEDED')
        self.assertEqual(git(self.repo,'rev-parse','HEAD'),self.before)
    def test_status_real_git(self):
        j=self.run_action('STATUS');self.assertEqual(j['state'],'SUCCEEDED');self.assertFalse(j['snapshot']['dirty']);self.assertEqual(j['snapshot']['branch'],BRANCH)
    def test_remote_ahead_check_does_not_modify_tree(self):
        target=self.remote_commit();j=self.run_action('CHECK');self.assertEqual(j['snapshot']['behind'],1);self.assertTrue(j['snapshot']['can_sync']);self.assertEqual(j['snapshot']['remote_commit'],target);self.assertEqual(git(self.repo,'rev-parse','HEAD'),self.before)
    def test_fast_forward_real_git(self):
        target=self.remote_commit();j=self.run_action('SYNC');self.assertEqual(j['state'],'SUCCEEDED');self.assertEqual(git(self.repo,'rev-parse','HEAD'),target);self.assertEqual(j['snapshot']['behind'],0)
    def test_equal_no_new_commit(self):
        j=self.run_action('SYNC');self.assertEqual(j['state'],'SUCCEEDED');self.assertEqual(git(self.repo,'rev-parse','HEAD'),self.before)
    def test_dirty_tracked_preserved(self):
        self.remote_commit();(self.repo/'file.txt').write_text('my changes');self.assert_blocked('REPO_DIRTY');self.assertEqual((self.repo/'file.txt').read_text(),'my changes')
    def test_untracked_preserved(self):
        (self.repo/'mine.txt').write_text('mine');self.assert_blocked('REPO_DIRTY');self.assertTrue((self.repo/'mine.txt').exists())
    def test_main_denied(self):
        git(self.repo,'checkout','-b','main');self.assert_blocked('BRANCH_DENIED')
    def test_explicit_quiz_main_profile_allowed_without_mutating_git(self):
        git(self.repo,'checkout','-b','main')
        self.engine.registry['animaquiz']['allow_main']=True
        job=self.run_action('STATUS')
        self.assertEqual(job['state'],'SUCCEEDED')
        self.assertEqual(job['snapshot']['branch'],'main')
        self.assertTrue(job['snapshot']['branch_allowed'])
        self.assertEqual(git(self.repo,'rev-parse','HEAD'),self.before)

    def test_master_denied(self):
        git(self.repo,'checkout','-b','master');self.assert_blocked('BRANCH_DENIED')
    def test_unexpected_branch_denied(self):
        git(self.repo,'checkout','-b','other');self.assert_blocked('BRANCH_DENIED')
    def test_wrong_remote_denied(self):
        git(self.repo,'remote','set-url','origin','https://example.invalid/repo');self.assert_blocked('REMOTE_MISMATCH')
    def test_multiple_remote_urls_denied(self):
        git(self.repo,'config','--add','remote.origin.url','https://example.invalid/other');self.assert_blocked('REMOTE_MISMATCH')
    def test_local_ahead_denied(self):
        (self.repo/'local.txt').write_text('local');git(self.repo,'add','.');git(self.repo,'commit','-m','local');self.before=git(self.repo,'rev-parse','HEAD');self.assert_blocked('LOCAL_AHEAD')
    def test_diverged_denied(self):
        self.remote_commit();(self.repo/'local.txt').write_text('local');git(self.repo,'add','.');git(self.repo,'commit','-m','local');self.before=git(self.repo,'rev-parse','HEAD');self.assert_blocked('NON_FAST_FORWARD')
    def test_dns_failure_and_explicit_check_retry(self):
        original=GitGuard.call
        def fail_fetch(guard,args,*a,**kw):
            if args[0]=='fetch': return 128,'','ssh: Could not resolve hostname github.com: Name or service not known'
            return original(guard,args,*a,**kw)
        with patch.object(GitGuard,'call',fail_fetch): j=self.run_action('CHECK')
        self.assertEqual(j['error_code'],'DNS_FAILED');self.assertEqual(j['exit_code'],128)
        self.assertEqual(j['state'],'FAILED');self.assertEqual(j['message'],MESSAGES['DNS_FAILED'])
        self.assertEqual(git(self.repo,'rev-parse','HEAD'),self.before)
        self.assertEqual(self.run_action('CHECK')['state'],'SUCCEEDED')
    def test_fetch_failure_preserves_checkout(self):
        self.project['origin']=str(self.base/'missing.git');git(self.repo,'remote','set-url','origin',self.project['origin']);self.assert_blocked('REMOTE_ACCESS_FAILED')
    def test_symlink_root_denied(self):
        alias=self.base/'alias';alias.symlink_to(self.repo,target_is_directory=True);self.project['root']=str(alias);self.assert_blocked('PATH_DENIED')
    def test_filter_config_denied(self):
        git(self.repo,'config','filter.evil.smudge','touch /tmp/never');self.assert_blocked('EXECUTABLE_CONFIG')
    def test_include_config_denied(self):
        git(self.repo,'config','include.path',str(self.base/'extra'));self.assert_blocked('EXECUTABLE_CONFIG')
    def test_url_rewrite_denied(self):
        git(self.repo,'config','url.ext::evil.insteadOf',str(self.remote));self.assert_blocked('EXECUTABLE_CONFIG')
    def test_hook_not_executed(self):
        self.remote_commit();sentinel=self.base/'hook-ran';hook=self.repo/'.git/hooks/post-merge';hook.write_text('#!/bin/sh\ntouch '+str(sentinel)+'\n');hook.chmod(0o700)
        self.assertEqual(self.run_action('SYNC')['state'],'SUCCEEDED');self.assertFalse(sentinel.exists())
    def test_fsmonitor_not_executed(self):
        sentinel=self.base/'fsmonitor-ran';script=self.base/'fsmonitor';script.write_text('#!/bin/sh\ntouch '+str(sentinel)+'\n');script.chmod(0o700);git(self.repo,'config','core.fsmonitor',str(script));self.assertEqual(self.run_action('STATUS')['state'],'SUCCEEDED');self.assertFalse(sentinel.exists())
    def test_repeated_start_same_id(self):
        jid=uuid.uuid4().hex;a=self.engine.start(jid,'animaquiz','CHECK',spawn=False);b=self.engine.start(jid,'animaquiz','CHECK',spawn=False);self.assertEqual(a,b)
        self.engine.run_job(jid);again=self.engine.start(jid,'animaquiz','CHECK',spawn=False);self.assertEqual(again['state'],'SUCCEEDED')
    def test_replay_conflict(self):
        jid=uuid.uuid4().hex;self.engine.start(jid,'animaquiz','CHECK',spawn=False)
        with self.assertRaises(Denied) as cm:self.engine.start(jid,'animaquiz','SYNC',spawn=False)
        self.assertEqual(cm.exception.code,'REPLAY_CONFLICT')
    def test_busy(self):
        self.engine.start(uuid.uuid4().hex,'animaquiz','CHECK',spawn=False)
        with self.assertRaises(Denied) as cm:self.engine.start(uuid.uuid4().hex,'animaquiz','STATUS',spawn=False)
        self.assertEqual(cm.exception.code,'JOB_BUSY')
    def test_global_build_reservation_rejects_second_project(self):
        # A second independent project must not start another Gradle job.
        self.engine.registry['second']=dict(self.project)
        self.engine.start(uuid.uuid4().hex,'animaquiz','BUILD_DEBUG',spawn=False)
        with self.assertRaises(Denied) as cm:
            self.engine.start(uuid.uuid4().hex,'second','BUILD_DEBUG',spawn=False)
        self.assertEqual(cm.exception.code,'JOB_BUSY')
        with self.assertRaises(Denied) as cm:
            self.engine.start(uuid.uuid4().hex,'second','TEST',spawn=False)
        self.assertEqual(cm.exception.code,'JOB_BUSY')
        self.assertEqual(git(self.repo,'rev-parse','HEAD'),self.before)
    def test_ctrl_c_before_gradle_spawn_does_not_start_any_command(self):
        jid=uuid.uuid4().hex
        self.engine.start(jid,'animaquiz','BUILD_DEBUG',spawn=False)
        cancelled=self.engine.cancel(jid,'animaquiz','BUILD_DEBUG')
        self.assertEqual(cancelled['state'],'INTERRUPTED')
        self.assertEqual(cancelled['error_code'],'CANCELLED')
        self.engine.run_job(jid)
        self.assertEqual(self.engine.query(jid,'animaquiz','BUILD_DEBUG')['state'],'INTERRUPTED')
        self.assertEqual(git(self.repo,'rev-parse','HEAD'),self.before)

    def test_cancel_running_verified_job_sets_one_shot_flag(self):
        jid=uuid.uuid4().hex
        self.engine.start(jid,'animaquiz','TEST',spawn=False)
        self.engine.store.update(jid,state='RUNNING',pid=os.getpid(),process_identity=identity(os.getpid()))
        result=self.engine.cancel(jid,'animaquiz','TEST')
        self.assertEqual(result['state'],'RUNNING')
        self.assertTrue(result['cancel_requested'])
        self.assertTrue(self.engine.cancel(jid,'animaquiz','TEST')['cancel_requested'])

    def test_ctrl_c_rejects_wrong_job_or_git_action(self):
        jid=uuid.uuid4().hex
        self.engine.start(jid,'animaquiz','CHECK',spawn=False)
        with self.assertRaises(Denied) as cm:
            self.engine.cancel(jid,'animaquiz','CHECK')
        self.assertEqual(cm.exception.code,'ACTION_DENIED')
        with self.assertRaises(Denied) as cm:
            self.engine.cancel(jid,'animaquiz','BUILD_DEBUG')
        self.assertEqual(cm.exception.code,'RESULT_MALFORMED')
        self.assertFalse(self.engine.query(jid,'animaquiz','CHECK').get('cancel_requested',False))

    def test_missing_query_tombstones_late_start(self):
        jid=uuid.uuid4().hex;a=self.engine.query(jid,'animaquiz','SYNC');self.assertEqual(a['state'],'NOT_STARTED');b=self.engine.start(jid,'animaquiz','SYNC',spawn=False);self.assertEqual(b['state'],'NOT_STARTED');self.engine.run_job(jid);self.assertEqual(git(self.repo,'rev-parse','HEAD'),self.before)
    def test_reopen_store_keeps_result(self):
        j=self.run_action('CHECK');e=Engine(self.base/'state',{'animaquiz':self.project});self.assertEqual(e.query(j['job_id'],'animaquiz','CHECK')['state'],'SUCCEEDED')
    def test_dead_pid_interrupted(self):
        jid=uuid.uuid4().hex;self.engine.start(jid,'animaquiz','CHECK',spawn=False);self.engine.store.update(jid,state='RUNNING',pid=99999999,process_identity='oldboot:1');self.assertEqual(self.engine.query(jid,'animaquiz','CHECK')['state'],'INTERRUPTED')
    def test_live_pid_not_interrupted(self):
        jid=uuid.uuid4().hex;self.engine.start(jid,'animaquiz','CHECK',spawn=False);self.engine.store.update(jid,state='RUNNING',pid=os.getpid(),process_identity=identity(os.getpid()));self.assertEqual(self.engine.query(jid,'animaquiz','CHECK')['state'],'RUNNING')
    def test_unknown_action_denied(self):
        with self.assertRaises(Denied) as cm:self.engine.start(uuid.uuid4().hex,'animaquiz','PUSH',spawn=False)
        self.assertEqual(cm.exception.code,'ACTION_DENIED')
    def test_unknown_project_denied(self):
        with self.assertRaises(Denied) as cm:self.engine.start(uuid.uuid4().hex,'other','STATUS',spawn=False)
        self.assertEqual(cm.exception.code,'PROJECT_NOT_FOUND')
    def test_id_traversal_denied(self):
        with self.assertRaises(Denied) as cm:self.engine.start('../escape','animaquiz','STATUS',spawn=False)
        self.assertEqual(cm.exception.code,'RESULT_MALFORMED')
    def test_timeout_owns_only_launched_process(self):
        guard=GitGuard(self.project,git_executable=shutil.which('git'))
        # A fixed test executable replaces Git only inside this isolated imported fixture.
        sleeper=self.base/'sleep.py';sleeper.write_text('#!/usr/bin/env python3\nimport time\ntime.sleep(5)\n');sleeper.chmod(0o700);guard.prefix=[str(sleeper)]
        with self.assertRaises(Denied) as cm:guard.call(['fixture'],timeout=.1)
        self.assertEqual(cm.exception.code,'TIMED_OUT')
    def test_dead_controller_live_child_blocks_replay(self):
        jid=uuid.uuid4().hex;self.engine.start(jid,'animaquiz','SYNC',spawn=False)
        self.engine.store.update(jid,state='RUNNING',pid=99999999,process_identity='old',child_pid=os.getpid(),child_identity=identity(os.getpid()))
        self.assertEqual(self.engine.query(jid,'animaquiz','SYNC')['state'],'UNKNOWN')
        with self.assertRaises(Denied) as cm:self.engine.start(uuid.uuid4().hex,'animaquiz','SYNC',spawn=False)
        self.assertEqual(cm.exception.code,'JOB_BUSY')
    def test_dead_controller_and_child_interrupted(self):
        jid=uuid.uuid4().hex;self.engine.start(jid,'animaquiz','SYNC',spawn=False)
        self.engine.store.update(jid,state='RUNNING',pid=99999999,process_identity='old',child_pid=99999998,child_identity='old')
        self.assertEqual(self.engine.query(jid,'animaquiz','SYNC')['state'],'INTERRUPTED')
    def test_child_identity_recorded_before_git_and_cleared(self):
        seen=[]
        guard=GitGuard(self.project,git_executable=shutil.which('git'),on_process=lambda pid,token:seen.append(pid))
        self.assertEqual(guard.text(['rev-parse','HEAD']),self.before)
        self.assertIsInstance(seen[0],int);self.assertIsNone(seen[1])
    def test_process_identity_available(self):
        self.assertNotIn(identity(os.getpid()),(None,'__UNKNOWN__'))
    def test_concurrent_reservation_only_one(self):
        jid=uuid.uuid4().hex
        with concurrent.futures.ThreadPoolExecutor(2) as pool:
            jobs=list(pool.map(lambda _:self.engine.reserve(jid,'animaquiz','SYNC'),[0,1]))
        self.assertEqual(sum(int(new) for _,new in jobs),1)
    def test_detached_worker_result_survives_parent_context(self):
        jid=uuid.uuid4().hex;self.engine.start(jid,'animaquiz','CHECK',spawn=False)
        worker=multiprocessing.get_context('fork').Process(target=self.engine.run_job,args=(jid,))
        worker.start();worker.join(10)
        self.assertEqual(worker.exitcode,0)
        reopened=Engine(self.base/'state',{'animaquiz':self.project})
        self.assertEqual(reopened.query(jid,'animaquiz','CHECK')['state'],'SUCCEEDED')
    def test_wire_logs_bounded_even_with_unicode(self):
        job={'stdout':'😀'*20000,'stderr':'😀'*20000,'message':'ok','snapshot':None}
        encoded=json.dumps(wire_job(job),ensure_ascii=False).encode()
        self.assertLess(len(encoded),24000)
    def test_plain_log_preserved(self):
        self.assertEqual(redact('ordinary Git output'), 'ordinary Git output')
    def test_snapshot_dirty_blocks_can_sync(self):
        self.remote_commit();(self.repo/'mine').write_text('mine');j=self.run_action('CHECK');self.assertTrue(j['snapshot']['dirty']);self.assertFalse(j['snapshot']['can_sync'])

class ErrorClassification(unittest.TestCase):
    def test_dns(self): self.assertEqual(classify_fetch_error('ssh: Could not resolve hostname github.com'), 'DNS_FAILED')
    def test_dns_temporary(self): self.assertEqual(classify_fetch_error('Temporary failure in name resolution'), 'DNS_FAILED')
    def test_network(self): self.assertEqual(classify_fetch_error('Network is unreachable'), 'NETWORK_UNAVAILABLE')
    def test_connection_timeout(self): self.assertEqual(classify_fetch_error('Connection timed out'), 'NETWORK_UNAVAILABLE')
    def test_ssh_auth(self): self.assertEqual(classify_fetch_error('Permission denied (publickey).'), 'SSH_AUTH_FAILED')
    def test_host_key(self): self.assertEqual(classify_fetch_error('REMOTE HOST IDENTIFICATION HAS CHANGED'), 'SSH_HOST_KEY_FAILED')
    def test_repository_missing(self): self.assertEqual(classify_fetch_error('Repository not found'), 'REMOTE_ACCESS_FAILED')
    def test_branch_missing(self): self.assertEqual(classify_fetch_error("couldn't find remote ref refs/heads/test"), 'REMOTE_ACCESS_FAILED')
    def test_unknown_is_fixed_message(self):
        raw='unexpected internal diagnostic'
        code=classify_fetch_error(raw)
        self.assertEqual(code,'FETCH_FAILED');self.assertNotIn(raw,MESSAGES[code])

if __name__=='__main__':unittest.main()
