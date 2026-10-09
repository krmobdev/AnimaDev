import pathlib
import tempfile
import unittest
from unittest.mock import patch
import animadev_runner as runner
import job_monitor as monitor

class ActivityTests(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory();self.addCleanup(self.tmp.cleanup)
        self.root=pathlib.Path(self.tmp.name);self.store=runner.Store(self.root/'state');self.journal=monitor.Journal(self.store,runner.redact)
        self.jid='b'*32
        self.job=dict(job_id=self.jid,project_id='animaquiz',fingerprint='x',action='TEST',state='RUNNING',created_at=100,pid=None,stdout='',stderr='')
        with self.store.transaction() as c:self.store.save(c,self.job)
    def test_cpu_increase_is_real_activity(self):
        self.store.update(self.jid,last_progress_at=100,child_pid=123,child_identity='boot:123')
        ticks=[1]
        original=pathlib.Path.read_text
        def read(p,*a,**k):
            if str(p)=='/proc/123/stat':return '(process) S 1 '+'0 '*9+str(ticks[0])+' 2 3 4 '+'0 '*4+'123'
            if str(p).endswith('/boot_id'):return 'boot'
            if str(p).endswith('/children'):return ''
            return original(p,*a,**k)
        with patch.object(pathlib.Path,'read_text',read):
            signals=monitor.Signals(self.store,self.jid,self.root);signals.tick(True);ticks[0]=10;signals.tick(True)
        with self.store.transaction() as c:
            j=self.store.get(c,self.jid);self.assertTrue(j['computing']);self.assertEqual(j['progress_signal'],'CPU')
    def test_timeout_is_ninety_minutes(self):
        import gradle_tests
        self.assertEqual(gradle_tests.TIMEOUT,90*60)
    def test_read_metadata_preserves_junit(self):
        self.job['tests']=dict(total=396,passed=396,failed=0,skipped=0,duration_ms=210056)
        self.assertEqual(self.journal.page(self.job)['metadata']['tests']['total'],396)
