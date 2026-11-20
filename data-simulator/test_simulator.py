"""Self-check: python3 -m unittest data-simulator/test_simulator.py (or run from data-simulator/)."""
import csv
import json
import random
import sys
import tempfile
import unittest
from datetime import datetime, timezone
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
import simulator as sim


def read_csv(path):
    with open(path, encoding="utf-8", newline="") as f:
        return list(csv.DictReader(f, delimiter=";"))


class SimulatorTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.out = Path(self.tmp.name)
        self.now = datetime(2026, 11, 21, 6, 15, tzinfo=timezone.utc)

    def tearDown(self):
        self.tmp.cleanup()

    def test_reference_files_parse(self):
        sim.write_reference(self.out, 50, "t", random.Random(1), self.now)
        rows = read_csv(self.out / "orchards.csv")
        self.assertEqual(list(rows[0].keys())[:3], ["externalRef", "name", "memberNumber"])
        self.assertGreaterEqual(len(rows), 50)
        codes = {v[0] for v in sim.VARIETIES}
        valid = [r for r in rows if r["varietyCode"] in codes and 7 <= float(r["lng"]) <= 12
                 and 30 <= float(r["lat"]) <= 38 and float(r["areaHa"]) > 0]
        self.assertGreater(len(valid), len(rows) * 0.9)
        self.assertEqual(len(json.loads((self.out / "varieties.json").read_text())["varieties"]), 4)
        self.assertEqual(len(json.loads((self.out / "mills.json").read_text())["mills"]), 5)
        self.assertEqual(len(read_csv(self.out / "resources.csv")), 560)

    def test_dynamic_files_parse_and_match_names(self):
        sim.write_dynamic(self.out, random.Random(2), "t", 50, self.now)
        weather = json.loads((self.out / "weather_202611210615.json").read_text())
        self.assertEqual(len(weather["readings"]), 6)
        self.assertIn("stationId", weather["readings"][0])
        maturity = read_csv(self.out / "maturity_202611210615.csv")
        self.assertGreater(len(maturity), 40)
        self.assertEqual(maturity[0]["orchardExternalRef"], "ORC-0001")
        cap = json.loads((self.out / "mill_capacity_20261121.json").read_text())
        self.assertEqual(cap["date"], "2026-11-21")
        self.assertEqual(len(cap["mills"]), 5)

    def test_invalid_records_are_injected(self):
        sim.write_maturity(self.out, random.Random(3), "t", 3000, sim.season_start_of(self.now), self.now)
        rows = read_csv(self.out / "maturity_202611210615.csv")
        bad = 0
        for r in rows:
            try:
                ok = 0 <= float(r["maturityIndex"]) <= 7 and r["orchardExternalRef"] != "ORC-9999"
            except ValueError:
                ok = False
            bad += not ok
        self.assertTrue(0.005 < bad / len(rows) < 0.05, bad)

    def test_history_writes_three_seasons(self):
        sim.write_history(self.out, random.Random(4), "t", 20, self.now)
        years = {p.name[8:12] for p in self.out.glob("weather_*.json")}
        self.assertEqual(years, {"2023", "2024", "2025"})


if __name__ == "__main__":
    unittest.main()
