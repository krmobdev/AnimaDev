"""Execute the APK's actual installer script in an isolated directory only."""
import base64
import hashlib
import json
import pathlib
import re
import subprocess
import sys
import tempfile
import textwrap
import unittest

class InstallerTests(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory();self.addCleanup(self.tmp.cleanup)
        self.root=pathlib.Path(self.tmp.name)/'state'
        self.source=pathlib.Path(__file__).parent.parent
        code=(self.source/'app/src/main/java/com/animacode/animadev/repos/RepoGateway.kt').read_text()
        self.script=textwrap.dedent(re.search(r'val script = """(.*?)""".trimIndent\(\)',code,re.S).group(1)).strip()
        self.script=self.script.replace('$RUNNER_ROOT',str(self.root)).replace('$VERSION','0.4.0')
        self.payloads=[]
        for name in ('animadev_runner.py','gradle_tests.py','job_monitor.py'):
            content=(self.source/'app/src/main/assets/runner'/name).read_bytes()
            self.payloads += [hashlib.sha256(content).hexdigest(),base64.b64encode(content).decode()]
    def run_install(self,values=None):
        r=subprocess.run([sys.executable,'-c',self.script,'a'*32,*(values or self.payloads)],capture_output=True,text=True)
        return r,json.loads(r.stdout)
    def test_actual_script_install(self):
        r,j=self.run_install();self.assertEqual(r.returncode,0);self.assertTrue(j['ok']);self.assertEqual(j['sha256'],self.payloads[0])
        self.assertEqual(hashlib.sha256((self.root/'releases'/self.payloads[0]/'animadev_runner.py').read_bytes()).hexdigest(),self.payloads[0])
    def test_repeat_preserves_existing_state_and_logs(self):
        self.root.mkdir();database=self.root/'jobs.sqlite3';database.write_bytes(b'existing fixture database');log=self.root/'saved-log';log.write_text('existing fixture log')
        self.run_install();self.run_install()
        self.assertEqual(database.read_bytes(),b'existing fixture database');self.assertEqual(log.read_text(),'existing fixture log')
    def test_bad_checksum_cannot_be_ready(self):
        values=list(self.payloads);values[0]='0'*64;r,j=self.run_install(values);self.assertNotEqual(r.returncode,0);self.assertFalse(j['ok'])
    def test_existing_changed_release_is_not_overwritten(self):
        self.run_install();p=self.root/'releases'/self.payloads[0]/'animadev_runner.py';p.write_text('changed fixture')
        r,j=self.run_install();self.assertFalse(j['ok']);self.assertEqual(p.read_text(),'changed fixture')
    def test_symlink_state_rejected(self):
        other=pathlib.Path(self.tmp.name)/'other';other.mkdir();self.root.symlink_to(other,target_is_directory=True)
        r,j=self.run_install();self.assertFalse(j['ok']);self.assertFalse((other/'releases').exists())
