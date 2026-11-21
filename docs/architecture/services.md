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
- Detect driving events: hard brake, harsh acceleration, speeding, distraction, crash.
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
  records what was already published, so a revision only emits events that are new.
- Crash (`DrivingEventDetector`, for `claims`): a sudden deceleration followed by a stop. The impact sample has a speed drop of at least
  40 km/h from the previous sample, or an acceleration at or below -15 m/s2, and every sample in the next 10 s is below 5 km/h
  (a trip that ends at the impact counts if that sample is below 5 km/h). A brake that keeps moving is a `hard_brake`, not a crash.
  The event is stamped with the impact sample; severity is the speed before the impact (`low` below 50 km/h, `medium` from 50, `high` from 80).
  Consecutive impact samples are one crash. The impact sample also breaks the hard-brake rule, so the crash replaces that `hard_brake`
  (one impact is not scored twice; `scoring` still weights `crash` at 25). `eventCounts` of `trip.completed` now includes `crash`.
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
- Consumes `trip.completed` only. `driving.event` is not consumed: the trip summary already carries `eventCounts`, and a new revision replaces them with the trip.
- Score is a rolling aggregate over the last N trips (N = 10, `SCORING_WINDOW_TRIPS`):
  - penalty = sum(weight x count) per 100 km, distance floored at 1 km. Weights: `hard_brake` 2, `harsh_accel` 2, `speeding` 3, `distraction` 4, `crash` 25; unknown kinds count 0.
  - trip score = max(0, 100 - penalty); driver score = mean of the last N trip scores (by `endedAt`); both rounded to 2 decimals.
- `score.updated` is published on every recompute, even when the value is unchanged; `scoreVersion` goes up by 1 each time.
- A trip event with a revision not newer than the stored one is ignored (no recompute, no event).
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
- One policy per driver, created on the first score with a base premium of 120.00 (`RATING_DEFAULT_BASE_PREMIUM`; no policy service exists).
- Score bands (lower bound inclusive) map to an adjustment of the base premium, rounded to cents:

  | Score | Band | Adjustment |
  |---|---|---|
  | >= 90 | `excellent` | -20% |
  | >= 75 | `good` | -10% |
  | >= 60 | `average` | 0% |
  | >= 40 | `poor` | +15% |
  | < 40 | `high_risk` | +30% |

- The adjustment is always applied to the base premium, never compounded on the previous premium.
- Stale scores (`scoreVersion` not higher than the one already applied) are ignored: no change, no event. `premium_adjustments` keeps the history, unique on `(driver_id, score_version)`.
- One `premium.adjusted` per applied score, keyed by `driverId`.
- Failures: unreadable or wrong-contract events (including a score outside 0-100) go straight to `score.updated.policy-rating.dlt`; anything else is retried 3 times first.
- API: `GET /v1/drivers/{driverId}/premium` returns the current rating, 404 until a score was applied.

## rewards (P2)

Why: risk mitigation, rewarding good drivers. Technically a leaf consumer with an external dependency,
so it is where retries, duplicates and outages get tested.

**Features**
- Consume `premium.adjusted` and decide whether a reward applies.
- Notify the driver through an external provider (mocked).
- No double rewards on duplicate events. Retry with backoff. Dead-letter on permanent failure.
- Expose a driver's rewards over HTTP.
- Health and readiness endpoints.

**Design**
- Consumes `premium.adjusted` only. Publishes no event (leaf service, so no outbox).
- Rule: a reward applies when the band is `excellent`. Kind `safe_driver_bonus`. At most one reward per `(driverId, scoreVersion)`.
  Any other band: no reward, no notification.
- DB: `rewards(reward_id UUID PK, driver_id, score_version, kind, status, attempts, created_at, notified_at)`, unique on
  `(driver_id, score_version)`. Status is `pending` then `notified`.
- Idempotency is the unique key, not an event ID table: a duplicate event, a late duplicate or the same score under a new event ID
  all find the same row. Pending rows are (re)notified, notified rows are left alone.
- Flow per message: (1) insert the reward row if new (own transaction); (2) if it is `pending`, call the provider outside any
  transaction; (3) on success mark it `notified`. A failure between 2 and 3 can notify twice, so the call carries the reward ID as
  `Idempotency-Key` and the provider dedupes on it (at-least-once, like every hop).
- Provider call: `POST {NOTIFICATION_URL}/v1/notifications` with `{rewardId, driverId, kind}` (no PII, see `docs/contracts/notification-provider.md`),
  timeout 2 s (`NOTIFICATION_TIMEOUT_MS`). The correlation ID travels in the `X-Correlation-Id` header.
- Failures: a provider `5xx`, `429` or timeout is retried 3 times with backoff, then dead-lettered to `premium.adjusted.rewards.dlt`.
  A provider `4xx` (not `429`) cannot succeed, so it is dead-lettered at once. Unreadable or wrong-contract events are dead-lettered at once.
  The reward row stays `pending` after a dead letter, so a replay (`replay-dlt.sh premium.adjusted.rewards.dlt`) notifies it once the provider is back.
- Stale or out-of-order scores: a reward for an older `scoreVersion` is still valid (it was earned), so there is no ordering check.
- API: `GET /v1/drivers/{driverId}/rewards` returns `{driverId, rewards: [{rewardId, scoreVersion, kind, status, createdAt, notifiedAt}]}`; an unknown driver gets an empty list (200).
- Mocked provider: WireMock deployment `notification-mock` in `platform/services/notification-mock` (decided). It accepts every
  notification with `202` by default. Tests read what it received and inject failures through its admin API (`/__admin`).

## claims (P2)

Why: claims modernization. Technically it correlates events from several services, so it is where
cross-service flows and eventual consistency get tested.

**Features**
- Detect a crash from `driving.event` (`kind: crash`, emitted by `trip-processor`) and open a first notice of loss (FNOL).
- Attach the trip evidence the event carries (trip, time, position, severity, correlation ID).
- Know the driver's policy by consuming `premium.adjusted` and keeping its own copy (decided: no service-to-service HTTP).
- Expose claim status over HTTP.
- Idempotent on duplicates, dead-letter with replay, health and readiness endpoints.

**Design**
- Consumes `driving.event` (every kind is read, only `crash` opens a claim; the others are ignored) and `premium.adjusted`.
  Publishes no event (leaf service, so no outbox), like `rewards`. Consumer group `claims` on both topics.
- DB (schema `claims`, user `claims`): `policies(driver_id PK, score_version, base_premium, new_premium, band, updated_at)`,
  `claims(claim_id UUID PK, driver_id, trip_id, crash_ts, severity, lat, lon, correlation_id, status, policy_score_version, created_at, updated_at)`,
  unique on `(trip_id, crash_ts)`, and `processed_events`.
- Policy copy: upserted from `premium.adjusted` only if `scoreVersion` is higher than the stored one (stale and duplicate events are ignored).
- Crash: one transaction inserts the claim with `ON CONFLICT (trip_id, crash_ts) DO NOTHING`, so a duplicate, a replay or the same crash
  under a new event ID finds the same row. Status:
  - `opened`: the driver's policy copy exists; `policy_score_version` records which version was current.
  - `pending_policy`: no policy copy yet. `premium.adjusted` and `driving.event` are different topics with no ordering between them,
    so the crash can arrive first. When the policy arrives, the driver's `pending_policy` claims move to `opened` in the same transaction.
- Claims are never deleted or downgraded; later states (review, closed) are out of scope for now.
- Failures: unreadable or wrong-contract events go straight to `<topic>.claims.dlt` (`driving.event.claims.dlt`, `premium.adjusted.claims.dlt`);
  anything else (database down) is retried 3 times with backoff first. Replay: `platform/scripts/replay-dlt.sh <topic>.claims.dlt`.
- API (port 8086):
  - `GET /v1/drivers/{driverId}/claims` returns `{driverId, claims: [{claimId, tripId, status, severity, crashAt, lat, lon, policyScoreVersion, createdAt}]}`, newest first; an unknown driver gets an empty list (200).
  - `GET /v1/claims/{claimId}` returns one claim, 404 if unknown.
  - Correlation ID in and out through `X-Correlation-Id`; no PII in logs.
- Test focus: crash to claim end to end, non-crash events ignored, duplicate crash gives one claim, crash before policy then policy arrives
  (`pending_policy` to `opened`), stale policy ignored, dead letter and replay, API 200/404.

## commons

- Event envelope: event ID, correlation ID, type, version, timestamps, source, payload.
- Correlation ID propagation (HTTP header, Kafka header, log context).
- Shared config and health conventions. No business logic.

## Cross-cutting

- One Postgres database, one schema per service (`ingest`, `trip`, `scoring`, `rating`), one DB user per service with grants only on its own schema. No shared tables, cross-schema joins or foreign keys. Each service runs its own migrations on its own schema.
- Correlation ID propagates through events, logs and HTTP calls.
- Failure handling for every consumer: retry, then DLT, then replay (see `event-catalog.md`).
