# data-simulator

Plain Python 3 script (standard library only) that plays the external systems of OliveConnect. It writes the files described in `docs/07` section 2 into the inbox directory read by the import scheduler.

```bash
python3 simulator.py ../inbox --only reference   # orchards.csv, varieties.json, mills.json, resources.csv
python3 simulator.py ../inbox --only dynamic     # weather_*.json, maturity_*.csv, mill_capacity_*.json
python3 simulator.py ../inbox --loop 15          # same, then new dynamic files every 15 minutes
python3 simulator.py ../inbox --history          # 3 past seasons of readings (about 100 000+ rows)
python3 -m unittest test_simulator               # self-check
```

Options: `--orchards N` (default 1500), `--seed S` (same seed gives the same orchards, so dynamic files always match the reference file).

- About 2 % of records are invalid on purpose (latitude outside Tunisia, unknown variety, negative area, humidity above 100, negative rain, `abc` as a number, unknown orchard or mill code, unknown resource type).
- Rain events are random; about one dynamic tick in four is rainy.
- Maturity rises about 0.05 per day from 1 October, with a different rate per variety.
- File formats not fixed in docs/07: `varieties.json` `{generatedAt, varieties:[{code,name,avgOilYieldPct,optimalMaturityMin,optimalMaturityMax}]}`; `mills.json` `{generatedAt, mills:[{code,name,lng,lat,dailyCapacityKg}]}`; `resources.csv` `externalRef;type;name;phone;capacityKg` with type `WORKER`, `TRACTOR` or `BIN`.
