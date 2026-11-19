# SafeDrive: Test platform

> Status: DRAFT, PARKED. Drafted early; we are finishing the app side first.

## Modules

- `integration-tests/core`: reusable pieces for every service: models, REST Assured clients,
  factories (`withX`), validators with child validators, constants, `AbstractTest`, and a waiter
  (poll with timeout) for eventual consistency.
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
    master.xml             calls smoke.xml and regression.xml
    smoke.xml              fast checks that the service is up and its main path works
    regression.xml         the full set of positive and negative feature classes
```

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
