"""Regression tests for dev.60 JKS creation + encrypted 7z backups; no user keys."""
from pathlib import Path
import hashlib
import os
import secrets
import subprocess
import tempfile
import unittest
import sys
from types import ModuleType
from unittest.mock import patch

ROOT=Path(__file__).resolve().parent
ns=ModuleType("keygen_test_fixture")
sys.modules[ns.__name__]=ns
exec(compile((ROOT/"secure_7z_pty.py").read_text(),str(ROOT/"secure_7z_pty.py"),"exec"),ns.__dict__)
exec(compile((ROOT/"keygen_archive_worker.py").read_text(),str(ROOT/"keygen_archive_worker.py"),"exec"),ns.__dict__)

class KeygenArchiveTests(unittest.TestCase):
    def setUp(self):
        folder=tempfile.TemporaryDirectory(prefix="keygen-test-")
        self.addCleanup(folder.cleanup)
        self.root=Path(folder.name)
        self.key=self.root/"release.jks"
        self.backup=self.root/"backup.7z"
        self.key.touch();self.backup.touch()
        self.spec=ns.KeySpec(secrets.token_hex(16),"release",2048,
            secrets.token_urlsafe(32),secrets.token_urlsafe(30))

    def test_real_keytool_and_sevenzip_encrypted_roundtrip(self):
        meta=ns.create_backup(self.spec,self.root,self.key,self.backup)
        self.assertEqual(meta["kind"],"JKS_BACKUP")
        self.assertEqual(meta["key_bits"],2048)
        self.assertEqual(meta["jks_sha256"],hashlib.sha256(self.key.read_bytes()).hexdigest())
        self.assertEqual(meta["archive_sha256"],hashlib.sha256(self.backup.read_bytes()).hexdigest())
        wrong=subprocess.run(["7z","t","-pWRONG_PASSWORD_FOR_TEST",str(self.backup)],
            stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,timeout=25)
        self.assertNotEqual(wrong.returncode,0)

    def test_archive_must_be_precreated_and_empty(self):
        self.backup.write_bytes(b"existing file")
        with self.assertRaises(ns.GenerateFailure) as e:
            ns.create_backup(self.spec,self.root,self.key,self.backup)
        self.assertEqual(e.exception.code,"OUTPUT_NOT_EMPTY")
        self.assertEqual(self.backup.read_bytes(),b"existing file")

    def test_invalid_alias_and_rsa_bits_rejected(self):
        for alias,bits in (("../unsafe",2048),("release",1024),("release",4090)):
            with self.subTest(alias=alias,bits=bits):
                with self.assertRaises(ns.GenerateFailure):
                    ns.KeySpec(self.spec.request_id,alias,bits,self.spec.jks_password,
                        self.spec.archive_password).validate()

    def test_separate_jks_and_backup_password_required(self):
        with self.assertRaises(ns.GenerateFailure):
            ns.KeySpec(self.spec.request_id,"release",4096,
                self.spec.jks_password,self.spec.jks_password).validate()

    def test_password_not_embedded_in_arguments(self):
        self.assertIn('"-storepass:file"',(ROOT/"keygen_archive_worker.py").read_text())
        self.assertIn('"-mhe=on"',(ROOT/"keygen_archive_worker.py").read_text())
        code=(ROOT/"keygen_archive_worker.py").read_text()
        self.assertNotIn('[sevenzip, "t", "-p", "-y"',code)
        with self.assertRaises(ns.ArchivePasswordError):
            ns.invoke(["7z","t","-p"+self.spec.archive_password],
                      self.spec.archive_password,self.root,timeout=2)

if __name__=="__main__":
    unittest.main()
