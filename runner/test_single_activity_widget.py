"""Static Android entry-point/widget regression checks without running AnimaBridge.

Run with: python -m unittest discover -s runner -p test_single_activity_widget.py -v
"""
from pathlib import Path
import re
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parent.parent
MANIFEST = ROOT / "app/src/main/AndroidManifest.xml"
JAVA = ROOT / "app/src/main/java/com/animacode/animadev"
RES = ROOT / "app/src/main/res"
NS = "{http://schemas.android.com/apk/res/android}"


class SingleActivityWidgetTest(unittest.TestCase):
    def test_only_launcher_activity_is_single_task(self):
        app = ET.parse(MANIFEST).getroot().find("application")
        activities = app.findall("activity")
        launcher = [a for a in activities
                    if a.attrib.get(NS + "name") == ".MainActivity"]
        self.assertEqual(len(launcher), 1)
        self.assertEqual(launcher[0].attrib.get(NS + "launchMode"), "singleTask")
        main = (JAVA / "MainActivity.kt").read_text(encoding="utf-8")
        self.assertIn("override fun onNewIntent(intent:Intent)", main)
        self.assertIn("setIntent(intent)", main)

    def test_every_external_entry_reuses_existing_task(self):
        filenames = (
            "termux/BridgeWidgetProvider.kt",
            "projects/ReleaseNotifications.kt",
            "repos/JobNotifications.kt",
            "projects/ArtifactGateway.kt",
        )
        for filename in filenames:
            with self.subTest(file=filename):
                code = (JAVA / filename).read_text(encoding="utf-8")
                self.assertIn("Intent.FLAG_ACTIVITY_NEW_TASK", code)
                self.assertIn("Intent.FLAG_ACTIVITY_CLEAR_TOP", code)
                self.assertIn("Intent.FLAG_ACTIVITY_SINGLE_TOP", code)
        widget = (JAVA / filenames[0]).read_text(encoding="utf-8")
        self.assertIn("Intent(context, MainActivity::class.java).apply {", widget)
        self.assertIn("PendingIntent.FLAG_IMMUTABLE", widget)

    def test_widget_layouts_fill_hosts_without_clipping_or_transparent_gap(self):
        for size, rows, buttons in (("compact", 2, 3), ("large", 3, 4)):
            root = ET.parse(RES / f"layout/bridge_widget_{size}.xml").getroot()
            with self.subTest(size=size):
                self.assertEqual(root.tag, "LinearLayout")
                self.assertEqual(root.attrib.get(NS + "layout_height"), "match_parent")
                self.assertEqual(root.attrib.get(NS + "background"), "@drawable/bridge_widget_background")
                self.assertEqual(root.attrib.get(NS + "gravity"), "center_vertical")
                text = (RES / f"layout/bridge_widget_{size}.xml").read_text()
                self.assertEqual(text.count("android:layout_height=\"" + "30dp" + "\""), buttons + (2 if size == "large" else 0))
                self.assertIn("@+id/bridge_widget_checked", text)
                self.assertEqual(root.attrib.get(NS + "gravity"), "center_vertical")
                if size == "compact":
                    self.assertIn("@+id/bridge_widget_summary", text)
                    self.assertIn("android:padding=\"6dp\"", text)
                else:
                    self.assertIn("android:padding=\"5dp\"", text)


    def test_two_widget_sizes_have_static_picker_previews(self):
        app = ET.parse(MANIFEST).getroot().find("application")
        receivers = [r.attrib.get(NS + "name") for r in app.findall("receiver")]
        for name in (".termux.BridgeWidgetProvider", ".termux.BridgeFullWidgetProvider"):
            self.assertIn(name, receivers)
        for name, height in (("bridge_widget_info", "2"), ("bridge_widget_full_info", "3")):
            root = ET.parse(RES / f"xml/{name}.xml").getroot()
            self.assertEqual(root.attrib.get(NS + "targetCellWidth"), "4")
            self.assertEqual(root.attrib.get(NS + "targetCellHeight"), height)
            self.assertEqual(root.attrib.get(NS + "resizeMode"), "horizontal|vertical")
            self.assertIsNone(root.attrib.get(NS + "previewLayout"))
            self.assertLessEqual(int(root.attrib.get(NS + "minHeight")[:-2]), 165)
            resource = root.attrib.get(NS + "previewImage")
            self.assertTrue(resource)
            self.assertTrue((RES / ("drawable/" + resource.split("/")[-1] + ".png")).is_file())
        source = (JAVA / "termux/BridgeWidgetProvider.kt").read_text(encoding="utf-8")
        self.assertIn("BridgeFullWidgetProvider", source)
        self.assertIn("layoutForProvider(full: Boolean)", source)
        self.assertNotIn("OPTION_APPWIDGET_MIN_HEIGHT", source)
        self.assertIn("R.id.bridge_widget_summary", source)


if __name__ == "__main__":
    unittest.main()
