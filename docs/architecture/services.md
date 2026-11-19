# SafeDrive: Service design

> Status: DRAFT. Events are defined in `event-catalog.md`. This file is also the source of truth for what
> each service must do (features), so tests are derived from it. Priority: P1 = core, P2 = phase 2.

## telemetry-ingest (P1, entry point)

Receives telemetry over HTTP, does cheap validation and deduplication, and forwards only clean data.
Owns a small Postgres schema: it is **not** stateless, because duplicate and race detection need state.

**Features**
- Accept trip batches over HTTP (GPS, speed, acceleration, braking, phone use).
- Validate payloads and reject malformed ones with a clear error format.
- Assign a correlation ID at the edge and publish validated telemetry to `trip.raw`.
- Tolerate duplicate batches (same batch ID) without double publishing, including concurrent duplicates.
- Health and readiness endpoints.

**API**
- `POST /v1/telemetry/batches` with `batchId`, `tripId`, `driverId`, `deviceId`, `batchSeq`, `last`, `samples[]`.
  Returns `202` accepted, `200` duplicate (already accepted, same content), `400` invalid, `409` conflict
  (same `batchId`, different content).

**Validation (cheap, no business logic)**
- Structure and required fields: reject the whole batch with `400`.
- Per-sample checks (lat/lon range, speed and acceleration bounds, timestamp not in the future or too old):
  invalid samples are dropped and reported back as `rejectedSamples`; the rest are forwarded.
- Timestamps ordered within the batch.
- Limits (inclusive): at most 5000 samples per batch; `lat` -90..90, `lon` -180..180, `speedKmh` 0..300,
  `accelMs2` -50..50, `heading` 0..360; `ts` an ISO-8601 UTC instant at most 5 minutes in the future and 7 days old.
  A sample with a missing field or a value outside these limits is dropped and reported; a batch with no valid
  sample, or with out-of-order timestamps among the valid ones, is rejected with `400`.

**Dedup and races**
- Table `ingested_batches(batch_id PK, trip_id, driver_id, payload_hash, status, received_at)`.
- Insert with `ON CONFLICT DO NOTHING`; the unique key is the single arbiter, so two concurrent identical
  requests produce exactly one publish.
- Same `batchId` and same hash: duplicate. Different hash: `409`.
- Table `outbox` written in the same transaction; a relay publishes to `trip.raw` and marks the row sent.
- Creates the `correlationId` if the caller did not send one.

## trip-processor (P1)

Consumes `trip.raw`, builds trips, detects driving events.

**Features**
- Assemble samples into trips, including late and out-of-order batches.
- Detect driving events: hard brake, harsh acceleration, speeding, distraction.
- Publish `driving.event` and `trip.completed`. Idempotent on event ID.
- Dead-letter topic for unprocessable events, with replay.
- Health and readiness endpoints.

**Design**
- DB: `trips`, `samples(trip_id, batch_seq)` unique, `processed_events`, `outbox`.
- Out of order: batches are stored by `batchSeq`; a trip closes when `last` arrived and there are no gaps,
  or after an inactivity timeout.
- Late data after close: stored, trip republished as `trip.completed` with `revision + 1`.
- Duplicates: dropped by `eventId` and by the `(trip_id, batch_seq)` unique key.

**As built**
- One transaction per message: `processed_events`, trip row lock, batch insert, and the outbox rows for the results.
- Detection runs over the whole assembled trip. Consecutive samples breaking the same rule are one event, stamped with
  the first sample of the run and the worst severity (`DrivingEventDetector` holds the thresholds). Table `driving_events`
  records what was already published, so a revision only emits events that are new. `crash` is not detected yet (claims, P2).
- Idle timeout: `TRIP_IDLE_TIMEOUT_S` (default 300) closes a trip whose last batch is missing or has a gap.
- `trip.completed` carries cumulative `eventCounts` for every revision.
- Failures: unreadable or wrong-contract events go straight to `trip.raw.trip-processor.dlt` (retrying cannot help);
  anything else is retried 3 times with backoff first. Each dead-lettering is logged with topic, offset, key and reason.
- Replay: `platform/scripts/replay-dlt.sh trip.raw.trip-processor.dlt` copies new dead letters back to `trip.raw`
  with key and envelope untouched. A message that still fails goes back to the dead-letter topic.

## scoring (P1)

Consumes `trip.completed` and `driving.event`, produces a driver risk score.

**Features**
- Compute and store a driver risk score.
- Recompute correctly when a trip is reprocessed (new revision).
- Expose the current score over HTTP.
- Publish `score.updated`.
- Health and readiness endpoints.

**Design**
- DB: `trip_scores(trip_id PK, revision)`, `driver_scores(driver_id PK, score_version)`, `processed_events`, `outbox`.
- Score is a rolling aggregate over the last N trips (formula kept simple and documented).
- A trip revision replaces the previous one, then the score is recomputed.
- API: `GET /v1/drivers/{driverId}/score`.
- `score.updated` carries a monotonic `scoreVersion`.

## policy-rating (P1)

Consumes `score.updated`, adjusts the premium.

**Features**
- Compute the premium or discount for the driver's policy.
- Expose the current rating over HTTP.
- Publish `premium.adjusted`.
- Health and readiness endpoints.

**Design**
- DB: `policies`, `premium_adjustments(driver_id, score_version)` unique, `processed_events`, `outbox`.
- Score bands map to a discount or surcharge percentage.
- Stale scores (lower `scoreVersion` than already applied) are ignored.
- API: `GET /v1/drivers/{driverId}/premium`.

## rewards (P2, not designed yet)

Why: risk mitigation, rewarding good drivers. Technically a leaf consumer with an external dependency,
so it is where retries, duplicates and outages get tested.

- Consume score or premium events and decide whether a reward applies.
- Send a notification through an external provider (mocked).
- No double rewards on duplicate events. Retry with backoff. Dead-letter on permanent failure.
- Decided: consumes `premium.adjusted`.
- **Open:** which module owns the mock provider (`simulator` or a small mock under `platform`).

## claims (P2, not designed yet)

Why: claims modernization. Technically it correlates events from several services, so it is where
cross-service flows and eventual consistency get tested.

- Detect a crash from `driving.event` (`kind: crash`, emitted by `trip-processor`).
- Open a first notice of loss (FNOL) and attach trip evidence.
- Know the driver's policy and expose claim status over HTTP.
- Decided: gets the policy by consuming `premium.adjusted` and keeping its own copy (no service-to-service HTTP).

## commons

- Event envelope: event ID, correlation ID, type, version, timestamps, source, payload.
- Correlation ID propagation (HTTP header, Kafka header, log context).
- Shared config and health conventions. No business logic.

## Cross-cutting

- One Postgres database, one schema per service (`ingest`, `trip`, `scoring`, `rating`), one DB user per service with grants only on its own schema. No shared tables, cross-schema joins or foreign keys. Each service runs its own migrations on its own schema.
- Correlation ID propagates through events, logs and HTTP calls.
- Failure handling for every consumer: retry, then DLT, then replay (see `event-catalog.md`).
