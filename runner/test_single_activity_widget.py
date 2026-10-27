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

    def test_widget_card_wraps_content_inside_transparent_host(self):
        for size in ("large", "medium", "compact"):
            file = RES / f"layout/bridge_widget_{size}.xml"
            root = ET.parse(file).getroot()
            with self.subTest(size=size):
                self.assertEqual(root.tag, "FrameLayout")
                self.assertEqual(root.attrib.get(NS + "layout_height"), "match_parent")
                self.assertNotIn(NS + "background", root.attrib)
                card = list(root)[0]
                self.assertEqual(card.tag, "LinearLayout")
                self.assertEqual(card.attrib.get(NS + "layout_height"), "wrap_content")
                self.assertEqual(card.attrib.get(NS + "gravity"), "top")
                self.assertEqual(card.attrib.get(NS + "layout_gravity"), "top")
                self.assertEqual(
                    card.attrib.get(NS + "background"), "@drawable/bridge_widget_background"
                )

    def test_home_widget_targets_three_rows_and_stays_resizable(self):
        root = ET.parse(RES / "xml/bridge_widget_info.xml").getroot()
        self.assertEqual(root.attrib.get(NS + "targetCellWidth"), "4")
        self.assertEqual(root.attrib.get(NS + "targetCellHeight"), "3")
        self.assertEqual(root.attrib.get(NS + "resizeMode"), "horizontal|vertical")


if __name__ == "__main__":
    unittest.main()
