# Track B — What I built and why

## Resources, alerts and field operations

Track B covers Worker, Equipment, Unavailability, ResourceBooking, SupplyOrder
and FieldAlert. Equipment uses one MongoDB collection with a discriminator for
Tractor, Bin and Tool, which keeps filtering and operational administration
simple while preserving subtype fields. Resource availability is checked
before a round is committed, and Redis locks protect the short critical section
around concurrent booking attempts.

FieldAlert accepts a geolocation and optional GridFS photo, then moves through
an explicit state machine. The alert event is persisted as a Notification and
published through Redis so SSE subscribers receive the event without making
Redis the source of truth.

## Evidence to show

- polymorphic equipment reads and duplicate-code alternative;
- calendar overlap and unavailability behavior;
- geolocation fallback and invalid photo-size alternative;
- the alert board, notification bell and SSE event test.

## Defence sentence

“The shared equipment collection is a deliberate NoSQL inheritance mapping: a
single operational list is more useful than one collection per subtype, while
the `type` discriminator and typed DTO preserve the fields that differ.”
