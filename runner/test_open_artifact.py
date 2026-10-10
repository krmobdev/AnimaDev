import hashlib
import importlib.util
import pathlib
import tempfile
import types
import unittest

FILE = pathlib.Path(__file__).resolve().parent.parent / "app/src/main/assets/artifact/open_artifact.py"
SPEC = importlib.util.spec_from_file_location("open_artifact", FILE)
opening = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(opening)

class ArtifactOpeningTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = pathlib.Path(self.temp.name)
        self.downloads = self.root / "Download"
        self.downloads.mkdir()
        self.cache = self.root / "cache"
        self.data = b"verified-artifact" * 200
        self.sha = hashlib.sha256(self.data).hexdigest()
        self.calls = []
    def tearDown(self):
        self.temp.cleanup()
    def fake_run(self, argv, **kwargs):
        self.calls.append(argv)
        self.assertEqual(self.data, pathlib.Path(argv[-1]).read_bytes())
        return types.SimpleNamespace(returncode=0)
    def call(self, path, sha=None, size=None):
        return opening.open_artifact(str(path), sha or self.sha, size or len(self.data),
            self.downloads, self.cache, "termux-open", self.fake_run)
    def artifact(self, extension="apk"):
        path = self.downloads / ("App_debug." + extension)
        path.write_bytes(self.data)
        return path
    def test_apk_launches_installer_mime_using_verified_private_copy(self):
        path = self.artifact()
        self.assertTrue(self.call(path)["ok"])
        self.assertEqual(["termux-open", "--view", "--content-type",
            "application/vnd.android.package-archive"], self.calls[0][:-1])
        self.assertEqual(self.cache / (self.sha + ".apk"), pathlib.Path(self.calls[0][-1]))
    def test_aab_is_opened_as_bundle_not_installable_apk(self):
        self.call(self.artifact("aab"))
        self.assertEqual("application/octet-stream", self.calls[0][3])
    def test_modified_file_does_not_open(self):
        path = self.artifact()
        path.write_bytes(b"x" * len(self.data))
        with self.assertRaisesRegex(ValueError, "ARTIFACT_CHANGED"):
            self.call(path)
        self.assertEqual([], self.calls)
        self.assertEqual([], list(self.cache.iterdir()))
    def test_wrong_size_does_not_open(self):
        with self.assertRaisesRegex(ValueError, "ARTIFACT_CHANGED"):
            self.call(self.artifact(), size=len(self.data)+1)
        self.assertEqual([], self.calls)
    def test_symlink_is_rejected(self):
        original = self.artifact()
        link = self.downloads / "Link.apk"
        link.symlink_to(original)
        with self.assertRaises(OSError):
            self.call(link)
        self.assertEqual([], self.calls)
    def test_path_outside_downloads_is_rejected(self):
        outside = self.root / "App.apk"
        outside.write_bytes(self.data)
        with self.assertRaisesRegex(ValueError, "ARTIFACT_INVALID"):
            self.call(outside)
        self.assertEqual([], self.calls)
    def test_opener_failure_is_not_reported_as_success(self):
        self.fake_run = lambda *args, **kwargs: types.SimpleNamespace(returncode=1)
        with self.assertRaisesRegex(ValueError, "OPEN_FAILED"):
            self.call(self.artifact())
