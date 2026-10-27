"""Isolated Release APK regression tests: synthetic secrets, no user keystore or export."""
import io
import json
import os
import pathlib
import shutil
import subprocess
import tempfile
import unittest
import zipfile
from unittest import mock

import release_signer as signer
import release_worker as worker

class ReleaseApkTests(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.dir=pathlib.Path(self.tmp.name)

    def fixture(self):
        unsigned=self.dir/"app-release-unsigned.apk"
        with zipfile.ZipFile(unsigned,"w") as z:
            z.writestr("AndroidManifest.xml",b"synthetic binary placeholder")
        return unsigned,self.dir/"aligned.apk",self.dir/"signed.apk"

    def test_apk_metadata_uses_bounded_gradle_output_name(self):
        root=self.dir
        folder=root/"app/build/outputs/apk/release"
        folder.mkdir(parents=True)
        expected={"variantName":"release","applicationId":"com.example.test",
                  "elements":[{"outputFile":"app-release-unsigned.apk",
                               "versionCode":7,"versionName":"1.7"}]}
        (folder/"output-metadata.json").write_text(json.dumps(expected))
        actual,identity=worker._apk_file(root)
        self.assertEqual(actual.name,"app-release-unsigned.apk")
        self.assertEqual(identity,{"applicationId":"com.example.test","versionCode":7,"versionName":"1.7"})
        expected["elements"][0]["outputFile"]="../../../../secret.apk"
        (folder/"output-metadata.json").write_text(json.dumps(expected))
        with self.assertRaises(worker.ReleaseFailure):
            worker._apk_file(root)

    def test_variant_is_allowlisted_before_receiving_secret(self):
        payload={"request_id":"a"*32,"project":{},"alias":"dummy",
                 "keystore":"","store_password":"fixture","key_password":"",
                 "variant":"RELEASE_UNTRUSTED"}
        with self.assertRaises(worker.ReleaseFailure) as caught:
            worker.run(payload)
        self.assertEqual(caught.exception.code,"RESULT_MALFORMED")

    def test_signing_flow_checks_apksigner_and_package_metadata(self):
        unsigned,aligned,signed=self.fixture()
        secret=bytearray(b"synthetic-only-not-a-real-jks")
        password=bytearray(b"synthetic-only-password")
        seen=[]
        expected={"applicationId":"com.example.test","versionCode":7,"versionName":"1.7"}
        def fake_run(cmd,**kwargs):
            seen.append(tuple(cmd[:2]))
            if cmd[0]=="zipalign":
                shutil.copyfile(unsigned,aligned)
                return subprocess.CompletedProcess(cmd,0,stdout=b"",stderr=b"")
            if cmd[0]=="apksigner" and cmd[1]=="sign":
                self.assertTrue("file:/proc/self/fd/" in " ".join(cmd))
                self.assertNotIn(password.decode()," ".join(cmd))
                self.assertNotIn(secret.decode()," ".join(cmd))
                self.assertEqual(len(kwargs["pass_fds"]),3)
                shutil.copyfile(aligned,signed)
                return subprocess.CompletedProcess(cmd,0,stdout=b"",stderr=b"")
            if cmd[0]=="apksigner" and cmd[1]=="verify":
                return subprocess.CompletedProcess(cmd,0,
                    stdout=b"Signer #1 certificate SHA-256 digest: "+b"a"*64+b"\n",
                    stderr=b"")
            if cmd[0]=="aapt":
                return subprocess.CompletedProcess(cmd,0,
                    stdout=b"package: name='com.example.test' versionCode='7' versionName='1.7'\n",stderr=b"")
            raise AssertionError(f"Unexpected safe tool: {cmd[0]}")
        with mock.patch.object(signer.subprocess,"run",side_effect=fake_run):
            meta=signer.sign_apk(unsigned,aligned,signed,secret,password,password,"fixture",expected)
        self.assertEqual(meta["signer_sha256"],"a"*64)
        self.assertEqual(meta["applicationId"],"com.example.test")
        self.assertEqual(meta["versionCode"],7)
        self.assertEqual(meta["versionName"],"1.7")
        self.assertEqual([c[0] for c in seen],["zipalign","apksigner","apksigner","aapt"])

    def test_apk_signature_requires_cert_verification(self):
        unsigned,aligned,signed=self.fixture()
        secret=bytearray(b"synthetic-only-not-a-real-jks")
        password=bytearray(b"synthetic-only-password")
        def fake_run(cmd,**kwargs):
            if cmd[0]=="zipalign":shutil.copyfile(unsigned,aligned)
            if cmd[0]=="apksigner" and cmd[1]=="sign":shutil.copyfile(aligned,signed)
            return subprocess.CompletedProcess(cmd,0,stdout=b"",stderr=b"")
        with mock.patch.object(signer.subprocess,"run",side_effect=fake_run):
            with self.assertRaises(signer.SigningError) as caught:
                signer.sign_apk(unsigned,aligned,signed,secret,password,password,"fixture",
                   {"applicationId":"com.example.test","versionCode":7,"versionName":"1.7"})
        self.assertEqual(caught.exception.code,"APK_SIGNATURE_INVALID")

if __name__=="__main__":
    unittest.main()
