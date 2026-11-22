# Track A — What I built and why

## Collection flow

Track A follows Orchard → HarvestRequest → CollectionRound → field execution →
MillReception. The central design choice is to keep the request and round
state machines explicit. `HarvestRequestService` owns transition and ownership
rules; `PlanningService` groups validated requests and `CollectionRoundService`
coordinates stops and bookings. A completed round is corrected through a
separate staff Correction path so ordinary edits cannot rewrite history.

I chose embedded Plot, RoundStop and status-history data because those values
are bounded and read with their owner. I chose snapshot data on a request and a
reception because an Orchard or User can change later while the operational
fact must remain explainable.

## Evidence to show

- nominal and alternative HarvestRequest API scenarios;
- priority queue and stale-version/invalid-transition responses;
- responsive Farmer, Collection manager, Team leader and Mill manager pages;
- reception and KPI tests, plus the traceability rows for the collection flow.

## Defence sentence

“The flow is a set of small stateful aggregates, not a single transactionally
large document: MongoDB transactions are used where round creation and resource
bookings must commit together, while embedded histories make each document's
timeline local and auditable.”
