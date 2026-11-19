# SafeDrive: Test platform

> Status: DRAFT, PARKED. Drafted early; we are finishing the app side first.

## Modules

- `integration-tests/core`: reusable pieces for every service: models, REST Assured clients,
  factories, fluent validators (master with child validators), constants, `AbstractTest`, and a waiter
  (poll with timeout) for eventual consistency.
  Packages under `com.qnt.safedrive.tests.core`: `client`, `factory`, `listener`, `model`
  (with `model.constants`: `Services`, `Topics`, `EventTypes`, `Http`, `Paths`, `Statuses`, `Groups`), `trace`, `utils` (`Json`, `TestEnvironment`, `Waiter`), `validator`.
  Models are Lombok records with `@With` and `@Builder(toBuilder = true)`, and use full-word field names
  (`timestamp`, not `ts`); where a contract uses a short wire name the field maps it with `@JsonProperty`.
  Factories return the model itself, so a test changes one field with `withX`:
  `aBatch().withTripId(null)`. Rules: `.claude/rules/testing.md`.
- `integration-tests/<service>`: black-box tests, one module per service, organized by feature.
- `integration-tests/e2e`: the full flow from ingest to premium. Special module, not tied to one service.
- `simulator`: generates trips for N drivers and injects faults (duplicate, out-of-order, late,
  malformed, burst). Used by tests and load tests.

## Run order

Per service: `smoke` first, then `regression` (what `master` does). Then `e2e`. Contract tests are
unit-level and run earlier, with `./mvnw test`.

## Conventions

TestNG, REST Assured, Allure. Tests are atomic and parallel-safe: every test creates its
own driver and trip IDs, and best-effort cleanup runs after it. Jackson 3 only (see code-style rules).

## Resources and suites (per service)

```
integration-tests/<service>/src/test/resources/
  environment.<env>.json   one file per environment (e.g. local, ci)
  suites/
    master.xml             calls smoke.xml then regression.xml; SmokeGateListener gates regression
    smoke.xml              group "smoke": fast checks that the service is up and its main path works
    regression.xml         group "regression": the full set of positive and negative tests
```

Suites select by package (`com.qnt.safedrive.tests.<service>.*`) and by TestNG group, so adding a test class
needs no suite edit. Each test is in exactly one group (`Groups` in `core`); health tests are always `smoke`.
`master` registers `SmokeGateListener` (in `core`): once any smoke test does not pass, every regression test is
skipped with the reason "smoke failed first", so a broken environment fails fast. Health tests are smoke only
and have no negative class: they only answer "is the service up".
Services, Postgres and Kafka are reached on localhost through `platform/scripts/port-forward.sh`.

The environment is picked with `-Denv=<env>`; the suite with `-Dsuite=<name>`. Environment files hold
no secrets (those come from env vars). `e2e` follows the same layout.

## Test structure inside a service

Tests are grouped by feature. Each feature has:

- `Abstract<Feature>Test`: extends `AbstractTest` from `core`; holds the feature's shared setup,
  helpers and cleanup.
- `<Feature>PositiveTest`: happy paths, extends the abstract class.
- `<Feature>NegativeTest`: invalid input, rejections and error paths, extends the abstract class.

```
integration-tests/telemetry-ingest/src/test/java/.../batch/
  AbstractBatchIngestTest
  BatchIngestPositiveTest
  BatchIngestNegativeTest
```

## Scoring tests (`integration-tests/scoring`)

Tests put `trip.completed` events straight on the topic (keyed by driver, built by `TripCompletedFactory`), so each
test controls the trip summary and therefore the expected score. They read `score.updated` from the topic and the
score from `GET /v1/drivers/{driverId}/score`. One driver per test keeps them parallel-safe.

| Feature | Covers |
|---|---|
| Health | liveness and readiness (smoke) |
| Score calculation | weights per 100 km, distance floor, zero floor, mean, rolling window of 10, `scoreVersion` +1 per trip, unknown kinds |
| Trip revisions | newer revision replaces the trip; older or repeated revision ignored, no gap in `scoreVersion` |
| Idempotency | duplicate event ID, late duplicate, same trip under a new event ID |
| Score API | 200 and 404 bodies, latest score matches `score.updated`, correlation ID header |
| Dead letter and replay | unreadable, wrong type or version, invalid payloads, partition not blocked, replay with `replay-dlt.sh` |

Run: `./mvnw -pl integration-tests/scoring -am test -Dsuite=master -Denv=local` (`-am` builds `core` first).

## Failure-mode matrix

| Scenario | Where it bites | What the test checks |
|---|---|---|
| Duplicate batch | `telemetry-ingest` | One publish; second call returns `200`; changed content returns `409` |
| Concurrent duplicates | `telemetry-ingest` | Exactly one event on `trip.raw` |
| Out-of-order batches | `trip-processor` | One correct trip regardless of arrival order |
| Late data after close | `trip-processor`, `scoring` | `trip.completed` revision + 1, score recomputed |
| Service down mid-pipeline | any consumer | No loss after restart; no double effect |
| Poison event | any consumer | Lands on the DLT with reason; replay succeeds once fixed |
| Eventual consistency | read APIs | Waiter converges; no fixed sleeps |

## Decision

The tracing helper follows a correlation ID through logs and topics only. Black-box tests do not read
service schemas, so there is no DB access.
