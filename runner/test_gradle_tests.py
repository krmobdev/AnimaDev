import multiprocessing
import pathlib
import subprocess
import tempfile
import time
import unittest
import uuid
from unittest.mock import patch
import animadev_runner as runner
import gradle_tests as gt
import test_runner as base

XML='<testsuite name="Fixture" tests="3" failures="1" errors="0" skipped="1" time="0.125"><testcase name="pass"/><testcase name="fail"><failure message="assertion"/></testcase><testcase name="skip"><skipped/></testcase></testsuite>'
OK='<testsuite name="Fixture" tests="1" failures="0" errors="0" skipped="0" time="0.25"><testcase name="pass"/></testsuite>'

def wrapper(root, xml=OK, exit_code=0, prefix=''):
    text='#!/bin/bash\n'+prefix+'\nmkdir -p app/build/test-results/testDebugUnitTest\nprintf "%s\\n" "$@" > app/build/args.txt\n'
    if xml is not None:text+="cat > app/build/test-results/testDebugUnitTest/TEST-fixture.xml <<'XML'\n"+xml+'\nXML\n'
    text+='exit '+str(exit_code)+'\n'
    (root/'gradlew').write_text(text)

class Reports(unittest.TestCase):
    def stats(self, xml):return gt.summary({}, {'file':('stamp',xml.encode())})
    def test_actual_counts(self):
        self.assertEqual(self.stats(XML),dict(total=3,passed=1,failed=1,skipped=1,duration_ms=125,report_files=1))
    def test_zero_tests_is_actual_zero(self):
        self.assertEqual(self.stats('<testsuite tests="0" time="0"/>')['total'],0)
    def test_stale_xml_not_success(self):
        old={'file':('stamp',OK.encode())}
        with self.assertRaises(gt.ReportError) as cm:gt.summary(old,old)
        self.assertEqual(cm.exception.code,'REPORTS_MISSING')
    def test_stale_leftover_ignored(self):
        old={'stale':('old',XML.encode())};after={**old,'new':('new',OK.encode())}
        self.assertEqual(gt.summary(old,after)['total'],1)
    def test_malformed_xml(self):
        with self.assertRaises(gt.ReportError):self.stats('<testsuite')
    def test_inconsistent_counts(self):
        with self.assertRaises(gt.ReportError):self.stats(OK.replace('tests="1"','tests="4"'))
    def test_doctype_denied(self):
        with self.assertRaises(gt.ReportError):self.stats('<!DOCTYPE testsuite>'+OK)
    def test_nonfinite_time_denied(self):
        with self.assertRaises(gt.ReportError):self.stats(OK.replace('0.25','nan'))
    def test_symlink_report_denied(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=pathlib.Path(tmp);d=root/'app/build/test-results/testDebugUnitTest';d.mkdir(parents=True)
            target=root/'external';target.write_text(OK);(d/'TEST-symlink.xml').symlink_to(target)
            with self.assertRaises(gt.ReportError):gt.reports(root)
    def test_private_block_content_suppressed(self):
        lines=gt.SafeLines(runner.redact);lines.private=True
        self.assertEqual(lines.feed('sensitive block content'), '')
    def test_truncated_channel_suppressed(self):
        lines=gt.SafeLines(runner.redact);lines.oversized()
        self.assertEqual(lines.feed('possibly sensitive continuation'), '')
    def test_stack_frames_omitted(self):
        self.assertEqual(gt.diagnostic('failure\n  at example.Run(Test.java:1)\nreason',runner.redact),'failure\nreason')

class GradleFixture(unittest.TestCase):
    setUp=base.Fixture.setUp
    tearDown=base.Fixture.tearDown
    run_action=base.Fixture.run_action
    def test_fixed_command_and_success(self):
        wrapper(self.repo)
        j=self.run_action('TEST');self.assertEqual(j['state'],'SUCCEEDED');self.assertEqual(j['exit_code'],0)
        self.assertEqual(j['tests']['passed'],1)
        self.assertEqual((self.repo/'app/build/args.txt').read_text().splitlines(),list(gt.COMMAND[1:]))
        self.assertEqual(base.git(self.repo,'rev-parse','HEAD'),self.before)
    def test_failure_counts_real_exit(self):
        wrapper(self.repo,XML,7);j=self.run_action('TEST')
        self.assertEqual(j['state'],'FAILED');self.assertEqual(j['exit_code'],7);self.assertEqual(j['tests']['failed'],1)
    def test_launch_without_report_not_success(self):
        wrapper(self.repo,None);j=self.run_action('TEST')
        self.assertEqual(j['error_code'],'REPORTS_MISSING');self.assertEqual(j['state'],'FAILED');self.assertIsNone(j['tests'])
    def test_old_reports_not_reused(self):
        wrapper(self.repo);self.assertEqual(self.run_action('TEST')['state'],'SUCCEEDED')
        wrapper(self.repo,None);j=self.run_action('TEST');self.assertEqual(j['error_code'],'REPORTS_MISSING')
    def test_wrapper_missing(self):
        self.assertEqual(self.run_action('TEST')['error_code'],'GRADLE_MISSING')
    def test_main_no_command_execution(self):
        wrapper(self.repo);base.git(self.repo,'checkout','-b','main')
        self.assertEqual(self.run_action('TEST')['error_code'],'BRANCH_DENIED');self.assertFalse((self.repo/'app/build/args.txt').exists())
    def test_long_output_bounded(self):
        wrapper(self.repo,prefix="for i in {1..3000}; do echo 'ordinary diagnostic line'; done")
        j=self.run_action('TEST');self.assertEqual(j['state'],'SUCCEEDED');self.assertLessEqual(len(j['stdout']),4096)
    def test_oversized_single_line_omitted(self):
        wrapper(self.repo,prefix="printf '%10000s\\n' x")
        j=self.run_action('TEST');self.assertEqual(j['state'],'SUCCEEDED');self.assertIn('пропущена',j['stdout'])
    def test_result_restored_without_callback(self):
        wrapper(self.repo);j=self.run_action('TEST')
        reopened=runner.Engine(self.base/'state',{'animaquiz':self.project})
        self.assertEqual(reopened.query(j['job_id'],'animaquiz','TEST')['tests']['total'],1)
        self.assertEqual(reopened.start(j['job_id'],'animaquiz','TEST',spawn=False)['state'],'SUCCEEDED')
    def test_long_job_conflicts_and_same_id(self):
        wrapper(self.repo,prefix='sleep 1')
        jid=uuid.uuid4().hex;self.engine.start(jid,'animaquiz','TEST',spawn=False)
        worker=multiprocessing.get_context('fork').Process(target=self.engine.run_job,args=(jid,));worker.start()
        try:
            deadline=time.monotonic()+5
            while self.engine.query(jid,'animaquiz','TEST')['state']=='QUEUED' and time.monotonic()<deadline:time.sleep(.02)
            self.assertEqual(self.engine.start(jid,'animaquiz','TEST',spawn=False)['job_id'],jid)
            with self.assertRaises(runner.Denied) as cm:self.engine.start(uuid.uuid4().hex,'animaquiz','SYNC',spawn=False)
            self.assertEqual(cm.exception.code,'JOB_BUSY')
            worker.join(10);self.assertEqual(worker.exitcode,0)
            self.assertEqual(self.engine.query(jid,'animaquiz','TEST')['state'],'SUCCEEDED')
        finally:
            if worker.is_alive():worker.terminate();worker.join()
    def test_git_job_blocks_tests(self):
        self.engine.start(uuid.uuid4().hex,'animaquiz','CHECK',spawn=False)
        with self.assertRaises(runner.Denied) as cm:self.engine.start(uuid.uuid4().hex,'animaquiz','TEST',spawn=False)
        self.assertEqual(cm.exception.code,'JOB_BUSY')
    def test_timeout_unknown_blocks_replay(self):
        wrapper(self.repo,prefix='sleep 5')
        jid=uuid.uuid4().hex;self.engine.start(jid,'animaquiz','TEST',spawn=False)
        guard=runner.GitGuard(self.project,git_executable=self.engine.git_executable)
        result=gt.execute(guard,lambda *a:None,lambda *a:None,runner.identity,runner.redact,timeout=.1)
        self.assertEqual(result['state'],'UNKNOWN');self.assertIsNone(result['exit_code'])
        self.engine.store.update(jid,**result,pid=9999999,process_identity='gone')
        self.assertEqual(self.engine.query(jid,'animaquiz','TEST')['state'],'UNKNOWN')
        with self.assertRaises(runner.Denied):self.engine.start(uuid.uuid4().hex,'animaquiz','TEST',spawn=False)
    def test_lost_controller_unknown(self):
        jid=uuid.uuid4().hex;self.engine.start(jid,'animaquiz','TEST',spawn=False)
        self.engine.store.update(jid,state='RUNNING',pid=9999999,process_identity='gone')
        self.assertEqual(self.engine.query(jid,'animaquiz','TEST')['state'],'UNKNOWN')
    def test_tombstone_blocks_late_test_start(self):
        wrapper(self.repo);jid=uuid.uuid4().hex
        self.assertEqual(self.engine.query(jid,'animaquiz','TEST')['state'],'NOT_STARTED')
        self.engine.start(jid,'animaquiz','TEST',spawn=False);self.engine.run_job(jid)
        self.assertFalse((self.repo/'app/build/args.txt').exists())
    def test_module_checksum_no_execution(self):
        wrapper(self.repo)
        with patch.object(runner,'GRADLE_SHA','0'*64):j=self.run_action('TEST')
        self.assertEqual(j['error_code'],'RUNNER_CHECKSUM_MISMATCH');self.assertFalse((self.repo/'app/build/args.txt').exists())


class DebugBuildFixture(unittest.TestCase):
    setUp=base.Fixture.setUp
    tearDown=base.Fixture.tearDown
    run_action=base.Fixture.run_action
    remote_commit=base.Fixture.remote_commit
    def setUp(self):
        base.Fixture.setUp(self)
        self.downloads=self.base/'Downloads'
        self.downloads.mkdir()
        self.sample=self.base/'sample.apk'
        import zipfile
        with zipfile.ZipFile(self.sample,'w') as apk:
            apk.writestr('AndroidManifest.xml',b'x'*2048)
    def prepare(self, create=True, code=0):
        cmd='#!/bin/sh\nmkdir -p app/build/outputs/apk/debug\n'
        if create: cmd += 'cp '+str(self.sample)+' app/build/outputs/apk/debug/app-debug.apk\n'
        cmd += 'exit '+str(code)+'\n'
        (self.repo/'gradlew').write_text(cmd)
        (self.repo/'.gitignore').write_text('app/build/\n')
        base.git(self.repo,'add','gradlew','.gitignore')
        base.git(self.repo,'commit','-m','fixture Gradle profile')
        base.git(self.repo,'push','origin',base.BRANCH)
        self.before=base.git(self.repo,'rev-parse','HEAD')
    def test_build_real_artifact_and_sha(self):
        self.prepare()
        with patch.object(gt,'DOWNLOADS',self.downloads):
            result=self.run_action('BUILD_DEBUG')
        self.assertEqual(result['state'],'SUCCEEDED',result)
        artifact=result['artifact']
        self.assertEqual(artifact['commit'],self.before)
        target=pathlib.Path(artifact['path'])
        self.assertTrue(target.is_file())
        import hashlib
        self.assertEqual(artifact['sha256'],hashlib.sha256(target.read_bytes()).hexdigest())
    def test_remote_ahead_blocks_before_gradle(self):
        self.prepare();base.git(self.seed,'pull','--ff-only','origin',base.BRANCH);self.remote_commit()
        with patch.object(gt,'DOWNLOADS',self.downloads):
            result=self.run_action('BUILD_DEBUG')
        self.assertEqual(result['error_code'],'BUILD_OUTDATED',result)
        self.assertFalse(list(self.downloads.glob('*.apk')))
    def test_dirty_tree_blocks_without_overwriting(self):
        self.prepare();(self.repo/'file.txt').write_text('changed')
        with patch.object(gt,'DOWNLOADS',self.downloads):
            result=self.run_action('BUILD_DEBUG')
        self.assertEqual(result['error_code'],'REPO_DIRTY',result)
        self.assertFalse(list(self.downloads.glob('*.apk')))
    def test_gradle_success_without_apk_not_accepted(self):
        self.prepare(create=False)
        with patch.object(gt,'DOWNLOADS',self.downloads):
            result=self.run_action('BUILD_DEBUG')
        self.assertEqual(result['error_code'],'BUILD_ARTIFACT_MISSING',result)
        self.assertFalse(list(self.downloads.glob('*.apk')))

if __name__=='__main__':unittest.main()
