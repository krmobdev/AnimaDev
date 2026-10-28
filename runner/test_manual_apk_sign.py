"""No real JKS, private keys or APKs used in these standalone-sign regressions."""
import base64
import hashlib
import pathlib
import subprocess
import tempfile
import unittest
import zipfile
from unittest import mock

SIGNER = pathlib.Path(__file__).with_name("release_signer.py")
WORKER = pathlib.Path(__file__).with_name("manual_apk_sign.py")
scope = {"__name__": "manual_fixture"}
exec(compile(SIGNER.read_text(), str(SIGNER), "exec"), scope)
exec(compile(WORKER.read_text(), str(WORKER), "exec"), scope)


class ManualApkSigningTests(unittest.TestCase):
    def setUp(self):
        t = tempfile.TemporaryDirectory()
        self.addCleanup(t.cleanup)
        self.root = pathlib.Path(t.name)
        self.downloads = self.root / "Download"
        self.downloads.mkdir()
        self.state = self.root / "state"
        self.state.mkdir()
        self.request = "a" * 32
        self.source = self.downloads / ("AnimaDev_unsigned_" + self.request + ".apk")
        with zipfile.ZipFile(self.source, "w") as z:
            z.writestr("AndroidManifest.xml", b"fake unsigned manifest")
        self.output = self.downloads / ("AnimaDev_signed_" + self.request + ".apk")
        self.output.write_bytes(b"")
        self.data = self.source.read_bytes()
        self.payload = {
            "request_id": self.request,
            "variant": "MANUAL_APK",
            "alias": "release",
            "keystore": base64.b64encode(b"x" * 50).decode("ascii"),
            "store_password": "synthetic",
            "key_password": "",
            "source_sha256": hashlib.sha256(self.data).hexdigest(),
            "source_size": len(self.data),
        }
        self.fake = mock.patch.dict(scope, {"DOWNLOADS": self.downloads, "STATE": self.state})
        self.fake.start()
        self.addCleanup(self.fake.stop)

    def sign(self, cmd, **kwargs):
        if cmd[:2] == ["apksigner", "verify"]:
            return subprocess.CompletedProcess(cmd, 1, stdout=b"", stderr=b"")
        if cmd[:3] == ["aapt", "dump", "badging"]:
            return subprocess.CompletedProcess(cmd, 0,
                stdout=b"package: name='com.example.manual' versionCode='42' versionName='1.0'\n",
                stderr=b"")
        raise AssertionError(cmd)

    def fake_sign(self, unsigned, aligned, signed, key, store, private, alias, expected):
        self.assertEqual(unsigned, self.source)
        self.assertEqual(expected["applicationId"], "com.example.manual")
        self.assertEqual(bytes(key), b"x" * 50)
        self.assertEqual(bytes(store), b"synthetic")
        self.assertEqual(bytes(private), b"synthetic")
        self.assertEqual(alias, "release")
        pathlib.Path(signed).write_bytes(b"verified signed synthetic APK contents")
        data = pathlib.Path(signed).read_bytes()
        return {
            "sha256": hashlib.sha256(data).hexdigest(),
            "size": len(data),
            "signer_sha256": "b" * 64,
            **expected,
        }

    def execute(self):
        with mock.patch.object(scope["subprocess"], "run", side_effect=self.sign):
            original = scope["sign_apk"]
            scope["sign_apk"] = self.fake_sign
            try:
                return scope["sign"](dict(self.payload))
            finally:
                scope["sign_apk"] = original

    def test_signs_staged_unsigned_source_preserves_original(self):
        result = self.execute()
        self.assertEqual(result["kind"], "MANUAL_APK")
        self.assertEqual(result["applicationId"], "com.example.manual")
        self.assertEqual(result["signer_sha256"], "b" * 64)
        self.assertEqual(self.source.read_bytes(), self.data)
        self.assertEqual(hashlib.sha256(self.output.read_bytes()).hexdigest(), result["sha256"])

    def test_source_changed_rejected_before_signing(self):
        self.payload["source_sha256"] = "f" * 64
        with self.assertRaises(scope["ManualSignFailure"]) as exc:
            self.execute()
        self.assertEqual(exc.exception.code, "SOURCE_CHANGED")

    def test_missing_android_owned_output_rejected(self):
        self.output.unlink()
        with self.assertRaises(scope["ManualSignFailure"]) as exc:
            self.execute()
        self.assertEqual(exc.exception.code, "OUTPUT_EXISTS")

    def test_preexisting_nonempty_output_cannot_be_overwritten(self):
        self.output.write_bytes(b"old file")
        with self.assertRaises(scope["ManualSignFailure"]) as exc:
            self.execute()
        self.assertEqual(exc.exception.code, "OUTPUT_EXISTS")
        self.assertEqual(self.output.read_bytes(), b"old file")

    def test_signed_input_is_rejected(self):
        def signed(cmd, **kwargs):
            if cmd[:2] == ["apksigner", "verify"]:
                return subprocess.CompletedProcess(cmd, 0)
            return self.sign(cmd, **kwargs)
        with mock.patch.object(scope["subprocess"], "run", side_effect=signed):
            with self.assertRaises(scope["ManualSignFailure"]) as exc:
                scope["sign"](dict(self.payload))
        self.assertEqual(exc.exception.code, "ALREADY_SIGNED")

    def test_malformed_and_extra_payload_keys_rejected(self):
        self.payload["unexpected"] = "value"
        with self.assertRaises(scope["ManualSignFailure"]) as exc:
            self.execute()
        self.assertEqual(exc.exception.code, "RESULT_MALFORMED")


if __name__ == "__main__":
    unittest.main()
