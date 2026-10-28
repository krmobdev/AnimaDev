"""Regression guards for dev.60 JKS generator and Build tab layout.

No user keys/passwords; real crypto round-trip is in test_keygen_archive.py.
"""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parent.parent
SOURCE = ROOT / "app/src/main/java/com/animacode/animadev/projects"

class KeygenIntegrationTests(unittest.TestCase):
    def test_build_tabs_fixed_equal_height_in_both_themes(self):
        regular = (SOURCE / "OperationSections.kt").read_text()
        neon = (SOURCE / "NeonGlassExtras.kt").read_text()
        self.assertIn("height(64.dp).semantics {this.selected=isSelected}", regular)
        self.assertIn("tabModifier.height(64.dp)", neon)
        self.assertIn("Modifier.horizontalScroll", regular)

    def test_generator_is_reachable_from_signing_tab(self):
        ui=(SOURCE / "ManualApkSigningPanel.kt").read_text()
        self.assertIn("KeyGeneratorPanel(context, project, permitted, termuxReady)",ui)
        self.assertIn("KeyGeneratorStore.generation.intValue",ui)
        panel=(SOURCE / "KeyGeneratorPanel.kt").read_text()
        self.assertIn("ActivityResultContracts.CreateDocument",panel)
        self.assertIn("2048,4096",panel)
        self.assertIn("archivePicker.launch",panel)
        self.assertIn("keyPicker.launch",panel)
        self.assertIn("secret==secretAgain",panel)

    def test_worker_and_one_time_secret_transport_are_bundled(self):
        gradle=(ROOT/"app/build.gradle.kts").read_text()
        self.assertIn('from(rootProject.file("runner/keygen_archive_worker.py"))',gradle)
        self.assertIn('from(rootProject.file("runner/secure_7z_pty.py"))',gradle)
        gateway=(SOURCE/"KeyGeneratorGateway.kt").read_text()
        self.assertIn("ReleaseHybridTransport.enroll(context,id)",gateway)
        self.assertIn("ReleaseHybridTransport.seal(id,public,request)",gateway)
        self.assertIn('putExtra("com.termux.RUN_COMMAND_STDIN",envelope)',gateway)
        self.assertIn("SecureRandom().nextBytes(it)",gateway)
        self.assertIn("SigningSecretVault(context)",gateway)

    def test_worker_does_not_put_secrets_on_command_line(self):
        worker=(ROOT/"runner/keygen_archive_worker.py").read_text()
        self.assertIn('"-storepass:file"',worker)
        self.assertIn('"-keypass:file"',worker)
        self.assertIn('"-mhe=on"',worker)
        self.assertIn("ARCHIVE_VERIFICATION_FAILED",worker)
        self.assertIn("PASSWORD_REUSE_FORBIDDEN",worker)
        self.assertIn("LOCK_EX | fcntl.LOCK_NB",worker)

    def test_result_receiver_is_private(self):
        doc=ET.parse(ROOT/"app/src/main/AndroidManifest.xml")
        ns="{http://schemas.android.com/apk/res/android}"
        nodes=[item for item in doc.findall("application/receiver")
               if item.attrib.get(ns+"name")==".projects.KeyGeneratorResultReceiver"]
        self.assertEqual(len(nodes),1)
        self.assertEqual(nodes[0].attrib.get(ns+"exported"),"false")

if __name__=="__main__":
    unittest.main()
