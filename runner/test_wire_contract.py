"""Keep Android receiver and the embedded Termux JSON envelope in lockstep.
Offline fixture: no user repo, no Android app storage, no real network calls.
"""
import contextlib
import hashlib
import io
import json
import pathlib
import re
import sys
import unittest
import uuid
from unittest.mock import patch
import animadev_runner as runner

ROOT=pathlib.Path(__file__).resolve().parents[1]

class RunnerWireContractTest(unittest.TestCase):
    def test_embedded_and_source_bytes_and_android_version_agree(self):
        android=(ROOT/'app/src/main/java/com/animacode/animadev/repos/RepoGateway.kt').read_text()
        declared=android.split('const val VERSION = ')[1].split(chr(34))[1]
        self.assertTrue(declared)
        self.assertEqual(declared,runner.VERSION)
        embedded=ROOT/'app/src/main/assets/runner/animadev_runner.py'
        source=ROOT/'runner/animadev_runner.py'
        self.assertEqual(embedded.read_bytes(),source.read_bytes())
        self.assertEqual(hashlib.sha256(embedded.read_bytes()).hexdigest(),
                         (embedded.parent/'runner.sha256').read_text().strip())

    def test_check_ack_uses_exact_android_wire_version(self):
        request=uuid.uuid4().hex
        job=uuid.uuid4().hex
        profile=uuid.uuid4().hex
        packet=dict(request_id=request,mode="START",job_id=job,
                    project_id=profile,action="CHECK")
        stub=type("EngineStub",(),{"start":lambda self,*args,**kw: {
            "job_id":job,"project_id":profile,"action":"CHECK",
            "state":"SUCCEEDED","exit_code":0}})()
        output=io.StringIO()
        with patch.object(runner,'Engine',return_value=stub),\
             patch.object(runner,'wire_job',side_effect=lambda o:o),\
             patch.object(sys,'argv',['animadev_runner.py',json.dumps(packet)]),\
             contextlib.redirect_stdout(output):
            self.assertEqual(runner.main(),0)
        response=json.loads(output.getvalue())
        self.assertEqual(response['protocol_version'],2)
        self.assertEqual(response['runner_version'],'0.4.1')
        self.assertEqual(response['request_id'],request)
        self.assertEqual(response['job']['state'],'SUCCEEDED')

if __name__=='__main__': unittest.main()
