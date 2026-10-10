"""Synthetic-only release signing tests: no production keystore or password is accessed."""
import hashlib
import io
import os
import pathlib
import subprocess
import tempfile
import unittest
import zipfile
from unittest import mock
import release_signer as signer

class ReleaseSignerTests(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory(prefix="animadev-aab-test-")
        self.addCleanup(self.temp.cleanup)
        root=pathlib.Path(self.temp.name)
        self.key=root/"synthetic.jks"
        self.bundle=root/"unsigned.aab"
        self.signed=root/"signed.aab"
        with zipfile.ZipFile(self.bundle,"w") as z:
            z.writestr("BundleConfig.pb",b"\x08\x01")
            z.writestr("base/manifest/AndroidManifest.xml",b"test")
            z.writestr("base/dex/classes.dex",b"fake-dex")
        command=["keytool","-genkeypair","-storetype","JKS",
                 "-keystore",str(self.key),"-storepass","fixture-store",
                 "-keypass","fixture-key","-alias","fixture",
                 "-dname","CN=AnimaDev Synthetic Only",
                 "-keyalg","RSA","-keysize","2048","-validity","1","-noprompt"]
        p=subprocess.run(command,capture_output=True,timeout=40)
        self.assertEqual(p.returncode,0,"Synthetic fixture generation failed")
        self.contents=bytearray(self.key.read_bytes())
        self.key.unlink() # prove signer never requires a keystore filesystem path

    def test_signs_and_verifies_aab_with_memfd_only(self):
        result=signer.sign_bundle(self.bundle,self.signed,self.contents,
                    bytearray(b"fixture-store"),bytearray(b"fixture-key"),"fixture")
        self.assertEqual(result["sha256"],hashlib.sha256(self.signed.read_bytes()).hexdigest())
        self.assertGreater(result["size"],1000)
        with zipfile.ZipFile(self.signed) as z:
            self.assertTrue(any(n.endswith(".SF") for n in z.namelist()))

    def test_rejects_wrong_private_key_password(self):
        with self.assertRaises(signer.SigningError) as caught:
            signer.sign_bundle(self.bundle,self.signed,self.contents,
                bytearray(b"fixture-store"),bytearray(b"incorrect"),"fixture")
        self.assertEqual(caught.exception.code,"KEY_OR_SIGNING_FAILED")

    def test_rejects_invalid_alias_and_oversize_secret(self):
        for alias in ["", "../fixture", "foo bar", "fixture;echo-secret"]:
            with self.assertRaises(signer.SigningError) as caught:
                signer.sign_bundle(self.bundle,self.signed,self.contents,
                    bytearray(b"fixture-store"),bytearray(b"fixture-key"),alias)
            self.assertEqual(caught.exception.code,"SIGNING_INPUT_DENIED")
        with self.assertRaises(signer.SigningError):
            signer.sign_bundle(self.bundle,self.signed,bytearray(signer.MAX_KEY_BYTES+1),
                bytearray(b"a"),bytearray(b"b"),"fixture")

    def test_does_not_write_signing_secret_to_disk(self):
        with mock.patch.object(signer.os,"memfd_create",side_effect=OSError("no memfd")):
            with self.assertRaises(OSError):
                signer.sign_bundle(self.bundle,self.signed,self.contents,
                    bytearray(b"fixture-store"),bytearray(b"fixture-key"),"fixture")
        self.assertFalse(self.signed.exists())
        self.assertEqual([x.name for x in pathlib.Path(self.temp.name).iterdir()],["unsigned.aab"])

if __name__=="__main__":
    unittest.main()
