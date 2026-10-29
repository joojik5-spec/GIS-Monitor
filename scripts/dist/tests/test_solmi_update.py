import hashlib
import json
from pathlib import Path
import sys
import tempfile
import unittest

BASE = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(BASE))
import solmi_update


class SolmiUpdateBundleTests(unittest.TestCase):
    def test_bundle_is_complete_and_reproducible(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            apk = root / "input.apk"
            apk.write_bytes(b"signed-apk-placeholder")
            notes = root / "notes.txt"
            notes.write_text("- Первое\n• Первое\n- Второе\n", encoding="utf-8")

            manifest = solmi_update.prepare_bundle(apk, root / "out", notes, 378)

            artifact = manifest["artifacts"][0]
            self.assertEqual(1, manifest["schema"])
            self.assertEqual(1678, manifest["versionCode"])
            self.assertEqual("1.3.0-Solmi.378", manifest["versionName"])
            self.assertEqual(26, manifest["minSdk"])
            self.assertEqual("ru.solmi.sensor", artifact["applicationId"])
            self.assertEqual("solmi-sensor.apk", artifact["file"])
            self.assertEqual("• Первое\n• Второе", manifest["notes"])
            self.assertEqual(hashlib.sha256(apk.read_bytes()).hexdigest(), artifact["sha256"])
            self.assertEqual(apk.stat().st_size, artifact["size"])
            saved = json.loads((root / "out" / "update-manifest.json").read_text(encoding="utf-8"))
            self.assertEqual(manifest, saved)

    def test_history_is_preserved_but_bounded(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            apk = root / "input.apk"
            apk.write_bytes(b"apk")
            notes = root / "notes.txt"
            notes.write_text("same", encoding="utf-8")
            previous = root / "previous.json"
            previous.write_text(json.dumps({"history": [
                {"code": i, "name": f"old-{i}", "notes": "• same"} for i in range(50)
            ]}), encoding="utf-8")

            manifest = solmi_update.prepare_bundle(apk, root / "out", notes, 9, previous)

            self.assertEqual(40, len(manifest["history"]))
            self.assertEqual(1309, manifest["history"][-1]["code"])
            self.assertEqual("• Исправления и улучшения", manifest["notes"])

    def test_invalid_run_number_is_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            apk = root / "input.apk"
            apk.write_bytes(b"apk")
            with self.assertRaises(ValueError):
                solmi_update.prepare_bundle(apk, root / "out", root / "missing", 0)


if __name__ == "__main__":
    unittest.main()
