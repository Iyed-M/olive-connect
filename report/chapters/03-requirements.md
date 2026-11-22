# Requirements analysis

The functional requirements are grouped around the collection flow (F1), field
alerts (F2), resources (F3), map (F4), dashboard (F5), and administration/data
integration. The complete requirement tables and use-case diagrams remain in
[`docs/02-requirements.md`](../../docs/02-requirements.md).

## Main user journeys

- A Farmer registers an Orchard with bounded Plot data and a GeoJSON position,
  declares a HarvestRequest, follows its StatusChange history and views their
  Mill receptions.
- A Collection manager validates requests, plans a CollectionRound, monitors
  alerts and reads KPIs.
- A Team leader runs assigned RoundStops in the field, including offline draft
  and retry behavior.
- A Logistics manager manages Workers, Equipment, Unavailability, bookings and
  SupplyOrders.
- A Mill manager receives a round, records weight and QualityAssessment, and
  monitors capacity and KPIs.
- An Admin manages Users, registration requests, imports, settings and the
  intranet views.

## Non-functional requirements

Security requires short-lived JWT access tokens, Redis-backed refresh sessions,
role checks, ownership checks and rate limiting. The performance target is a
map p95 below two seconds at 100 virtual users, with an error rate below one
percent. The deployment must be recoverable, monitored and usable on a 360 px
phone layout. These requirements are traced to tests and operations evidence
in [chapter 8](08-testing.md) and [chapter 9](09-deployment.md).
