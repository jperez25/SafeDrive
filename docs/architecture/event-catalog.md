# SafeDrive: Event catalog

> Status: DRAFT. Format: JSON Schema (Avro is the fallback if we need a performance upgrade).
> Schemas live in `docs/contracts/events/` (`<type>.v1.schema.json`, plus `envelope.v1.schema.json` and `examples/`).

## Envelope (in `commons`)

Every event, on every topic, is wrapped in the same envelope.

| Field | Type | Notes |
|---|---|---|
| `eventId` | UUID | Unique per event. Consumers dedupe on it. |
| `type` | string | e.g. `trip.raw` |
| `version` | int | Schema version, bumped on any breaking contract change. |
| `occurredAt` | ISO-8601 UTC | When it happened in the real world. |
| `producedAt` | ISO-8601 UTC | When the service published it. |
| `correlationId` | string | Created at the edge, never changed. |
| `source` | string | Producing service. |
| `payload` | object | Event specific, see below. |

The Kafka message key is chosen per topic to guarantee ordering where it matters.

## Topics

| Topic | Key | Producer | Consumer | Purpose |
|---|---|---|---|---|
| `trip.raw` | `tripId` | `telemetry-ingest` | `trip-processor` | Validated batch of samples |
| `trip.completed` | `driverId` | `trip-processor` | `scoring` | Trip summary, one per closed trip (revision aware) |
| `driving.event` | `driverId` | `trip-processor` | `scoring`, `claims` (P2) | Hard brake, harsh accel, speeding, distraction, crash |
| `score.updated` | `driverId` | `scoring` | `policy-rating` | New risk score for a driver |
| `premium.adjusted` | `driverId` | `policy-rating` | `rewards` (P2); `claims` (P2, planned) | New premium or discount |
| `<topic>.<consumer-group>.dlt` | original key | any consumer | replay tool | Unprocessable events |

Locally: 3 partitions per topic, replication factor 1.

## Payloads

**`trip.raw`**: `batchId`, `tripId`, `driverId`, `deviceId`, `batchSeq` (0-based, per trip), `last` (bool),
`samples[]` of `{ts, lat, lon, speedKmh, accelMs2, heading, phoneInUse}`.

**`trip.completed`**: `tripId`, `driverId`, `revision`, `startedAt`, `endedAt`, `distanceKm`,
`durationS`, `avgSpeedKmh`, `maxSpeedKmh`, `eventCounts{}`.

**`driving.event`**: `tripId`, `driverId`, `kind`, `severity`, `ts`, `lat`, `lon`.

**`score.updated`**: `driverId`, `scoreVersion` (monotonic), `score` (0-100), `basedOnTrips`, `computedAt`.

**`premium.adjusted`**: `driverId`, `scoreVersion`, `basePremium`, `adjustmentPct`, `newPremium`, `band`.

## Delivery rules

- At-least-once. Producers use a transactional outbox, so a DB write and its event never diverge.
- Consumers are idempotent: a `processed_events(event_id)` table, written in the same transaction as the effect.
- Retry 3 times with backoff, then publish to the DLT with the failure reason in headers.
- DLT events are replayable with the original envelope intact (same `eventId`, same `correlationId`).
