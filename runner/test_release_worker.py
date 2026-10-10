"""Release worker protocol/guard regressions; never uses a real JKS or password."""
import contextlib
import io
import json
import pathlib
import sys
import unittest
from unittest import mock

SCRIPTS=pathlib.Path(__file__).resolve().parent

def load_worker():
    code=(SCRIPTS/"release_signer.py").read_text()+"\n"+(SCRIPTS/"release_worker.py").read_text()
    scope={"__name__":"release_worker_test"}
    exec(compile(code,"release-worker-combined","exec"),scope)
    return scope

class ReleaseProtocolTests(unittest.TestCase):
    def setUp(self):
        self.worker=load_worker()
    def test_denies_untrusted_project_path_and_remote(self):
        check=self.worker["_valid_project"]
        error=self.worker["ReleaseFailure"]
        for req in [{}, {"id":"no","root":"/tmp/user","remote":"github.com/a/b","branch":"main","name":"unsafe"},
                    {"id":"no","root":"/data/data/com.termux/files/home/storage","remote":"github.com/a/b","branch":"main","name":"storage"}]:
            with self.assertRaises(error):
                check(req)

    def test_protocol_rejects_extraneous_keys_before_build(self):
        run=self.worker["run"]
        error=self.worker["ReleaseFailure"]
        with self.assertRaises(error) as caught:
            run({"request_id":"a"*32,"project":{},"alias":"key0","keystore":"",
                 "store_password":"synthetic-only","key_password":"","unexpected":"value"})
        self.assertEqual(caught.exception.code,"RESULT_MALFORMED")

    def test_secure_channel_rejects_bad_transport_checksum(self):
        scope=self.worker
        with mock.patch.object(sys,"argv",["-c","a"*32,"bad_checksum"]):
            with self.assertRaises(scope["ReleaseFailure"]) as e:
                scope["_receive_secure_payload"]()
        self.assertEqual(e.exception.code,"CHANNEL_DENIED")

    def test_worker_reply_does_not_echo_untrusted_input(self):
        scope=self.worker
        marker="FAKE_SENSITIVE_MARKER_DO_NOT_PRINT"
        reply={"request_id":"a"*32,"project":{},"alias":"key0","keystore":"",
                "store_password":marker,"key_password":marker}
        out=io.StringIO()
        with mock.patch.dict(scope,{"_receive_secure_payload":lambda:reply}),contextlib.redirect_stdout(out):
            scope["main"]()
        text=out.getvalue()
        parsed=json.loads(text)
        self.assertEqual(parsed["request_id"],"a"*32)
        self.assertFalse(parsed["ok"])
        self.assertEqual(parsed["error_code"],"PROJECT_DENIED")
        self.assertNotIn(marker,text)

if __name__=="__main__":
    unittest.main()
