"""Structural regressions for standalone APK signing: no user keystore fixture."""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parent.parent
PROJECT=ROOT/"app/src/main/java/com/animacode/animadev/projects"

class ManualSigningIntegrationTests(unittest.TestCase):
    def test_fourth_tab_does_not_rebuild_project(self):
        ui=(PROJECT/"BuildScreen.kt").read_text()
        self.assertIn('"Debug APK","Release APK","Release AAB","Подпись APK"',ui)
        self.assertIn("if(buildTab==3)item",ui)
        self.assertIn("ManualApkSigningPanel(context,project,permitted,termuxVersion!=null)",ui)
        self.assertIn("if(buildTab in 1..2)item",ui)

    def test_signer_worker_bundled_inside_app(self):
        gradle=(ROOT/"app/build.gradle.kts").read_text()
        self.assertIn('from(rootProject.file("runner/manual_apk_sign.py"))',gradle)
        gateway=(PROJECT/"ManualApkSignGateway.kt").read_text()
        self.assertIn('assets.open("release/manual_apk_sign.py")',gateway)
        self.assertIn("ReleaseHybridTransport.enroll(context, id)",gateway)
        self.assertIn("ReleaseHybridTransport.seal(id, public, payload)",gateway)
        self.assertIn('putExtra("com.termux.RUN_COMMAND_STDIN", sealed)',gateway)
        self.assertNotIn('putExtra("com.termux.RUN_COMMAND_STDIN", password)',gateway)

    def test_source_and_destination_scoped_storage_ownership(self):
        gateway=(PROJECT/"ManualApkSignGateway.kt").read_text()
        self.assertIn("MediaStore.Downloads.EXTERNAL_CONTENT_URI",gateway)
        self.assertIn('"AnimaDev_unsigned_$id.apk"',gateway)
        self.assertIn('"AnimaDev_signed_$id.apk"',gateway)
        self.assertIn("DocumentsContract.deleteDocument",gateway)
        self.assertIn("digest(stream, it)",gateway)

    def test_only_explicit_non_exported_callback(self):
        ns="{http://schemas.android.com/apk/res/android}"
        root=ET.parse(ROOT/"app/src/main/AndroidManifest.xml").getroot()
        receivers=root.find("application").findall("receiver")
        targets=[v for v in receivers if v.attrib.get(ns+"name")==".projects.ManualApkSignResultReceiver"]
        self.assertEqual(len(targets),1)
        self.assertEqual(targets[0].attrib.get(ns+"exported"),"false")

    def test_multiple_keys_and_vault_encrypted(self):
        keyring=(PROJECT/"SigningKeyring.kt").read_text()
        self.assertIn("signing_keyring_v1",keyring)
        self.assertIn("SigningSecretVault(context).save",keyring)
        self.assertIn("fun setDefault(project: String, profile: Profile)",keyring)
        self.assertIn("signing.saveDocument(project, Uri.parse(profile.uri)",keyring)
        self.assertIn("fun importLegacy(project: String)",keyring)

    def test_four_regular_tabs_are_not_cramped(self):
        tabs=(PROJECT/"OperationSections.kt").read_text()
        self.assertIn("labels.size>3",tabs)
        self.assertIn("Modifier.horizontalScroll",tabs)
        self.assertIn("Modifier.width(132.dp)",tabs)

if __name__=="__main__":
    unittest.main()
