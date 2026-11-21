#!/usr/bin/env python3
"""OliveConnect data simulator (docs/07 section 2 and 5). Python 3 standard library only.

Examples:
  python3 simulator.py /data/inbox --only reference
  python3 simulator.py /data/inbox --only dynamic
  python3 simulator.py /data/inbox --loop 15        # dynamic files every 15 minutes
  python3 simulator.py /data/inbox --history        # 3 past seasons of readings
"""
import argparse
import csv
import json
import math
import random
import time
from datetime import datetime, timedelta, timezone
from pathlib import Path

INVALID_RATE = 0.02  # share of records that are broken on purpose

# name, centre lng, centre lat (around Sfax), radius in degrees
DELEGATIONS = [
    ("Sakiet Ezzit", 10.76, 34.80, 0.05),
    ("Sakiet Eddaier", 10.68, 34.74, 0.05),
    ("El Amra", 10.62, 34.72, 0.06),
    ("Agareb", 10.53, 34.74, 0.07),
    ("Menzel Chaker", 10.40, 34.95, 0.07),
    ("Jebeniana", 10.90, 35.03, 0.07),
]
# code, name, avgOilYieldPct, optimalMaturityMin, optimalMaturityMax, maturity rise per day
VARIETIES = [
    ("CHEMLALI", "Chemlali", 20, 3.0, 4.5, 0.05),
    ("CHETOUI", "Chetoui", 18, 3.5, 5.0, 0.045),
    ("ZALMATI", "Zalmati", 19, 3.0, 4.5, 0.055),
    ("OUESLATI", "Oueslati", 17, 3.5, 5.0, 0.04),
]
# code, name, lng, lat, dailyCapacityKg
MILLS = [
    ("MIL-SFX-01", "Huilerie Sfax Nord", 10.76, 34.82, 60000),
    ("MIL-SFX-02", "Huilerie Sakiet Eddaier", 10.69, 34.75, 45000),
    ("MIL-AGR-01", "Huilerie Agareb", 10.53, 34.74, 50000),
    ("MIL-MCH-01", "Huilerie Menzel Chaker", 10.40, 34.95, 40000),
    ("MIL-JEB-01", "Huilerie Jebeniana", 10.90, 35.03, 35000),
]
STATIONS = [("SFX-%02d" % (i + 1), d[0]) for i, d in enumerate(DELEGATIONS)]
FIRST_NAMES = ["Ahmed", "Mohamed", "Ali", "Salah", "Karim", "Fatma", "Amel", "Sami", "Nabil", "Hedi"]
LAST_NAMES = ["Ben Salah", "Trabelsi", "Jebali", "Mezghani", "Chaabane", "Gharbi", "Kammoun", "Ayadi"]


def fmt_ts(dt):
    return dt.astimezone(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


def stamp(dt, pattern):
    return dt.astimezone(timezone.utc).strftime(pattern)


def write_csv(path, header, rows):
    with open(path, "w", encoding="utf-8", newline="") as f:
        w = csv.writer(f, delimiter=";", lineterminator="\n")
        w.writerow(header)
        w.writerows(rows)


def write_json(path, data):
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=1)


def pick_invalid(rng):
    return rng.random() < INVALID_RATE


# ---------- reference data ----------

def orchard_profile(i, seed):
    """Deterministic profile of orchard number i (1-based), so dynamic files match the reference file."""
    rng = random.Random("%s-orchard-%d" % (seed, i))
    delegation = rng.choice(DELEGATIONS)
    angle, dist = rng.uniform(0, 2 * math.pi), delegation[3] * math.sqrt(rng.random())
    variety = rng.choice(VARIETIES)
    return {
        "ref": "ORC-%04d" % i,
        "name": "Henchir %s %d" % (rng.choice(["El Mezraa", "Ennakhla", "Zitouna", "Es Sedra", "El Ain"]), i),
        "member": "ADH-%04d" % rng.randint(1, 500),
        "lng": round(delegation[1] + dist * math.cos(angle), 4),
        "lat": round(delegation[2] + dist * math.sin(angle), 4),
        "delegation": delegation[0],
        "variety": variety,
        "offset": rng.uniform(0.5, 2.0),  # maturity index at the start of the season
        "rng": rng,
    }


def write_reference(out, count, seed, rng, now):
    rows = []
    for i in range(1, count + 1):
        p = orchard_profile(i, seed)
        r = p["rng"]
        plots = []
        for n in range(r.choice([1, 1, 2, 3])):
            v = p["variety"] if n == 0 else r.choice(VARIETIES)
            area = round(r.uniform(0.5, 6), 1)
            plots.append(["P%d" % (n + 1), v[0], area, int(area * 100), r.randint(1960, 2020),
                          str(r.random() < 0.3).lower()])
        for plot in plots:
            row = [p["ref"], p["name"], p["member"], p["lng"], p["lat"], p["delegation"]] + plot
            if pick_invalid(rng):  # one broken field per bad row
                kind = rng.choice(["lat", "variety", "area"])
                if kind == "lat":
                    row[4] = 48.85  # outside Tunisia
                elif kind == "variety":
                    row[7] = "XYZ"  # unknown variety
                else:
                    row[8] = -1.0  # areaHa must be > 0
            rows.append(row)
    write_csv(out / "orchards.csv",
              ["externalRef", "name", "memberNumber", "lng", "lat", "delegation", "plotCode", "varietyCode",
               "areaHa", "treeCount", "plantingYear", "irrigated"], rows)

    write_json(out / "varieties.json", {"generatedAt": fmt_ts(now), "varieties": [
        {"code": c, "name": n, "avgOilYieldPct": y, "optimalMaturityMin": lo, "optimalMaturityMax": hi}
        for c, n, y, lo, hi, _ in VARIETIES]})

    mills = [{"code": c, "name": n, "lng": lng, "lat": lat, "dailyCapacityKg": cap}
             for c, n, lng, lat, cap in MILLS]
    write_json(out / "mills.json", {"generatedAt": fmt_ts(now), "mills": mills})

    res = []
    for i in range(1, 301):
        res.append(["WRK-%04d" % i, "WORKER", "%s %s" % (rng.choice(FIRST_NAMES), rng.choice(LAST_NAMES)),
                    "2%07d" % rng.randint(0, 9999999), ""])
    for i in range(1, 61):
        res.append(["TRC-%03d" % i, "TRACTOR", "Tractor %d" % i, "", rng.choice([2000, 3000, 5000])])
    for i in range(1, 201):
        res.append(["BIN-%04d" % i, "BIN", "Bin %d" % i, "", rng.choice([300, 400, 500])])
    for row in res:
        if pick_invalid(rng):
            row[1] = "UNKNOWN_TYPE"
    write_csv(out / "resources.csv", ["externalRef", "type", "name", "phone", "capacityKg"], res)


# ---------- dynamic data ----------

def weather_readings(rng, at, rainy):
    """One reading per station. 'rainy' is a shared rain event for the day."""
    readings = []
    for station, delegation in STATIONS:
        rain = round(rng.uniform(0.5, 8), 1) if rainy and rng.random() < 0.8 else 0.0
        r = {"stationId": station, "delegation": delegation, "timestamp": fmt_ts(at),
             "temperatureC": round(rng.uniform(8, 24), 1), "humidityPct": rng.randint(45, 95),
             "rainfallMm": rain, "windKmh": rng.randint(0, 35)}
        if pick_invalid(rng):
            kind = rng.choice(["humidity", "rain", "temp"])
            if kind == "humidity":
                r["humidityPct"] = 140
            elif kind == "rain":
                r["rainfallMm"] = -3.0
            else:
                r["temperatureC"] = "abc"
        readings.append(r)
    return readings


def write_weather(out, rng, at, rainy):
    write_json(out / ("weather_%s.json" % stamp(at, "%Y%m%d%H%M")),
               {"generatedAt": fmt_ts(at), "source": "SIM-WEATHER", "readings": weather_readings(rng, at, rainy)})


def maturity_index(p, season_start, at):
    days = max((at - season_start).total_seconds() / 86400, 0)
    return min(p["offset"] + p["variety"][5] * days, 7.0)


def season_start_of(now):
    year = now.year if now.month >= 10 else now.year - 1
    return datetime(year, 10, 1, tzinfo=timezone.utc)


def write_maturity(out, rng, seed, count, season_start, at, sample=1.0):
    rows = []
    for i in range(1, count + 1):
        if sample < 1.0 and rng.random() > sample:
            continue
        p = orchard_profile(i, seed)
        idx = round(maturity_index(p, season_start, at) + rng.uniform(-0.1, 0.1), 1)
        idx = min(max(idx, 0.0), 7.0)
        row = [p["ref"], fmt_ts(at), idx, "SENSOR"]
        if pick_invalid(rng):
            kind = rng.choice(["nan", "range", "ref"])
            if kind == "nan":
                row[2] = "abc"
            elif kind == "range":
                row[2] = 9.5
            else:
                row[0] = "ORC-9999"
        rows.append(row)
    write_csv(out / ("maturity_%s.csv" % stamp(at, "%Y%m%d%H%M")),
              ["orchardExternalRef", "timestamp", "maturityIndex", "source"], rows)


def write_mill_capacity(out, rng, at):
    mills = []
    for code, _, _, _, cap in MILLS:
        m = {"millCode": code, "dailyCapacityKg": cap, "availableCapacityKg": int(cap * rng.uniform(0.2, 1.0))}
        if pick_invalid(rng):
            if rng.random() < 0.5:
                m["availableCapacityKg"] = -500
            else:
                m["millCode"] = "MIL-XXX-99"
        mills.append(m)
    write_json(out / ("mill_capacity_%s.json" % stamp(at, "%Y%m%d")), {"date": at.strftime("%Y-%m-%d"), "mills": mills})


def write_dynamic(out, rng, seed, count, now):
    # about one tick in four is a rain event, so the heavy-rain alert gets triggered
    write_weather(out, rng, now, rainy=rng.random() < 0.25)
    write_maturity(out, rng, seed, count, season_start_of(now), now)
    write_mill_capacity(out, rng, now)


def write_history(out, rng, seed, count, now):
    """3 past seasons (1 Oct - 31 Dec): one weather file per 3 h, one maturity file per day (half of the orchards)."""
    for back in (3, 2, 1):
        start = datetime(season_start_of(now).year - back, 10, 1, tzinfo=timezone.utc)
        day = start
        rainy_until = start
        while day < start + timedelta(days=92):
            if rng.random() < 0.05:  # rain event lasting 1 to 2 days
                rainy_until = day + timedelta(days=rng.randint(1, 2))
            for hour in range(0, 24, 3):
                at = day + timedelta(hours=hour)
                write_weather(out, rng, at, rainy=at < rainy_until)
            write_maturity(out, rng, seed, count, start, day + timedelta(hours=6), sample=0.5)
            write_mill_capacity(out, rng, day)
            day += timedelta(days=1)
    print("history written to", out)


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("inbox", help="inbox directory (created if missing)")
    ap.add_argument("--only", choices=["reference", "dynamic", "all"], default="all")
    ap.add_argument("--history", action="store_true", help="generate 3 past seasons of readings")
    ap.add_argument("--loop", type=int, metavar="MIN", help="write dynamic files every MIN minutes")
    ap.add_argument("--orchards", type=int, default=1500)
    ap.add_argument("--seed", default="oliveconnect")
    args = ap.parse_args()

    out = Path(args.inbox)
    out.mkdir(parents=True, exist_ok=True)
    rng = random.Random(args.seed)
    now = datetime.now(timezone.utc).replace(second=0, microsecond=0)

    if args.history:
        write_history(out, rng, args.seed, args.orchards, now)
        return
    if args.only in ("reference", "all"):
        write_reference(out, args.orchards, args.seed, rng, now)
        print("reference files written to", out)
    if args.only in ("dynamic", "all"):
        write_dynamic(out, rng, args.seed, args.orchards, now)
        print("dynamic files written to", out)
    while args.loop:
        time.sleep(args.loop * 60)
        now = datetime.now(timezone.utc).replace(second=0, microsecond=0)
        write_dynamic(out, random.Random(), args.seed, args.orchards, now)
        print("dynamic files written at", fmt_ts(now))


if __name__ == "__main__":
    main()
