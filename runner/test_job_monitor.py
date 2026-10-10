import importlib.util
import json
import pathlib
import sqlite3
import tempfile
import time
import unittest
from unittest.mock import patch
import animadev_runner as runner
import job_monitor as monitor

class MonitorTests(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory();self.addCleanup(self.tmp.cleanup)
        self.root=pathlib.Path(self.tmp.name)
        self.store=runner.Store(self.root/'state')
        self.journal=monitor.Journal(self.store,runner.redact)
        self.jid='a'*32
        self.job=dict(job_id=self.jid,project_id='animaquiz',fingerprint='x',action='TEST',state='RUNNING',created_at=time.time(),pid=None,stdout='',stderr='')
        with self.store.transaction() as c:self.store.save(c,self.job)
    def all(self):
        cursor=0;text=''
        while True:
            p=self.journal.page(self.job,cursor);text+=p['text'];cursor=p['next_offset']
            if not p['more']:return text,cursor
    def test_real_cpu_utilization_formula_and_unknown_samples(self):
        self.assertEqual(monitor.cpu_usage_percent(100,200,1.0,100),100.0)
        self.assertEqual(monitor.cpu_usage_percent(100,350,1.0,100),250.0)
        self.assertIsNone(monitor.cpu_usage_percent(None,100,1,100))
        self.assertIsNone(monitor.cpu_usage_percent(200,100,1,100))
        self.assertIsNone(monitor.cpu_usage_percent(100,200,0,100))

    def test_available_memory_reads_only_linux_memavailable(self):
        path=self.root/'meminfo'
        path.write_text('MemTotal: 100000 kB\nMemAvailable: 512000 kB\n')
        self.assertEqual(monitor.available_memory_mib(str(path)),500)
        path.write_text('MemTotal: 100000 kB\\n')
        self.assertIsNone(monitor.available_memory_mib(str(path)))

    def test_old_schema_migration_preserves_job(self):
        self.job['stdout']='legacy output'
        with self.store.transaction() as c:self.store.save(c,self.job)
        page=self.journal.page(self.job)
        self.assertTrue(page['legacy']);self.assertIn('legacy output',page['text'])
        with self.store.transaction() as c:self.assertEqual(self.store.get(c,self.jid)['stdout'],'legacy output')
    def test_long_log_roundtrip_pages(self):
        with self.store.transaction() as c:c.execute('INSERT INTO journal_meta(job) VALUES(?)',(self.jid,))
        payload=''.join('line %05d useful output\n'%i for i in range(2000))
        self.journal.write(self.jid,'stdout',payload)
        text,cursor=self.all()
        self.assertIn('line 00000',text);self.assertIn('line 01999',text)
        self.assertGreater(len(text),4096);self.assertGreater(cursor,1)
    def test_resume_from_saved_cursor(self):
        self.journal.write(self.jid,'stdout','line\n'*3000)
        page=self.journal.page(self.job)
        second=monitor.Journal(self.store,runner.redact).page(self.job,page['next_offset'])
        self.assertEqual(second['offset'],page['next_offset']);self.assertGreater(second['next_offset'],page['next_offset'])
    def test_limit_explicit_no_jobs_removed(self):
        with patch.object(monitor,'MAX_BYTES',100):
            self.journal.write(self.jid,'stdout','x\n'*100)
        self.assertTrue(self.journal.page(self.job)['truncated'])
        with self.store.transaction() as c:self.assertIsNotNone(self.store.get(c,self.jid))
    def test_page_byte_limit(self):
        self.journal.write(self.jid,'stdout','я'*1000+'\n'+'z\n'*5000)
        self.assertLess(len(self.journal.page(self.job)['text'].encode()),7000)
    def test_secret_split_across_every_character(self):
        value='gh'+'p_'+'SYNTHETIC_FAKE_TEST_VALUE_123'
        for char in 'token='+value+'\ncontinuedFAKEvalue\n\nsafe\n':self.journal.write(self.jid,'stdout',char)
        text,_=self.all();self.assertNotIn(value,text);self.assertNotIn('continuedFAKEvalue',text);self.assertIn('safe',text)
        raw=self.store.db.read_bytes();self.assertNotIn(value.encode(),raw)
    def test_private_key_split_chunks_and_lines(self):
        fake='-----BEGIN '+'PRIVATE KEY-----\nFAKE_PRIVATE_MATERIAL\n-----END PRIVATE KEY-----\nsafe\n'
        for i in range(0,len(fake),3):self.journal.write(self.jid,'stderr',fake[i:i+3])
        text,_=self.all();self.assertNotIn('FAKE_PRIVATE_MATERIAL',text);self.assertIn('safe',text)
    def test_auth_url_masked_before_disk(self):
        value='https://fakeuser:FAKEPASSWORD@example.invalid/'
        self.journal.write(self.jid,'stdout',value+'\n')
        text,_=self.all();self.assertNotIn('FAKEPASSWORD',text)
    def test_password_multiline(self):
        self.journal.write(self.jid,'stdout','password:\nFAKE_VALUE\n\nokay\n')
        text,_=self.all();self.assertNotIn('FAKE_VALUE',text)
    def test_oversized_partial_line_suppressed(self):
        self.journal.write(self.jid,'stdout','x'*9000)
        self.journal.write(self.jid,'stdout','FAKE_SECRET_CONTINUATION\n')
        text,_=self.all();self.assertNotIn('FAKE_SECRET_CONTINUATION',text)
    def test_flush_at_end(self):
        self.journal.write(self.jid,'stdout','last line without newline');self.journal.flush(self.jid)
        text,_=self.all();self.assertIn('last line without newline',text)
    def test_important_mode_task_and_result(self):
        self.journal.write(self.jid,'stdout','ordinary\n')
        self.journal.write(self.jid,'stdout','> Task :app:testDebugUnitTest\n')
        self.journal.write(self.jid,'event','Exit code: 0',True)
        p=self.journal.page(self.job,mode='IMPORTANT');self.assertNotIn('ordinary',p['text']);self.assertIn('Exit code',p['text']);self.assertIn('> Task',p['text'])
    def test_reading_does_not_record_progress(self):
        self.store.update(self.jid,last_progress_at=100)
        self.journal.page(self.job);self.journal.history('animaquiz',0)
        with self.store.transaction() as c:self.assertEqual(self.store.get(c,self.jid)['last_progress_at'],100)
    def test_idle_observation_not_progress(self):
        self.store.update(self.jid,last_progress_at=100)
        signals=monitor.Signals(self.store,self.jid,self.root);signals.tick(True);signals.tick(True)
        with self.store.transaction() as c:
            j=self.store.get(c,self.jid);self.assertEqual(j['last_progress_at'],100);self.assertIsNone(j['process_alive']);self.assertFalse(j['computing'])
    def test_real_output_progress_and_task(self):
        signals=monitor.Signals(self.store,self.jid,self.root);signals.output('> Task :app:compileJava')
        with self.store.transaction() as c:
            j=self.store.get(c,self.jid);self.assertEqual(j['progress_signal'],'OUTPUT');self.assertIn('compileJava',j['stage'])
    def test_live_process_without_cpu_increase(self):
        self.store.update(self.jid,last_progress_at=100,child_pid=123,child_identity='boot:123')
        stat=') S 1 '+'0 '*9+'1 2 3 4 '+'0 '*4+'123'
        original=pathlib.Path.read_text
        def read(p,*a,**k):
            if str(p)=='/proc/123/stat':return '(process'+stat
            if str(p).endswith('/boot_id'):return 'boot'
            if str(p).endswith('/children'):return ''
            return original(p,*a,**k)
        with patch.object(pathlib.Path,'read_text',read):
            signals=monitor.Signals(self.store,self.jid,self.root);signals.tick(True);signals.tick(True)
        with self.store.transaction() as c:
            j=self.store.get(c,self.jid);self.assertTrue(j['process_alive']);self.assertEqual(j['last_progress_at'],100);self.assertFalse(j['computing'])
    def test_reports_change_progress(self):
        folder=self.root/'app/build/test-results/testDebugUnitTest';folder.mkdir(parents=True)
        signals=monitor.Signals(self.store,self.jid,self.root);signals.tick(True)
        (folder/'TEST-new.xml').write_text('<testsuite/>');signals.tick(True)
        with self.store.transaction() as c:self.assertEqual(self.store.get(c,self.jid)['progress_signal'],'REPORTS')
    def test_unknown_not_cleared_by_heartbeat(self):
        self.store.update(self.jid,state='UNKNOWN',unresolved=True,last_progress_at=100)
        monitor.Signals(self.store,self.jid,self.root).tick(True)
        with self.store.transaction() as c:self.assertEqual(self.store.get(c,self.jid)['state'],'UNKNOWN')
    def test_invalid_cursor_and_mode(self):
        for offset,view in [(-1,'ALL'),(True,'ALL'),(0,'SHELL')]:
            with self.assertRaises(ValueError):self.journal.page(self.job,offset,view)
    def test_history_owned_project_only(self):
        self.assertEqual(len(self.journal.history('other',0)['items']),0)
    def test_history_no_megabyte_payload(self):
        self.job['stdout']='x'*1000000
        with self.store.transaction() as c:self.store.save(c,self.job)
        self.assertLess(len(json.dumps(self.journal.history('animaquiz',0))),1000)

if __name__=='__main__':unittest.main()
