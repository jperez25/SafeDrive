# SafeDrive: Test platform

> Status: BUILT for the four core services, e2e, reports, CI and load. Open items: Toxiproxy, pinned load thresholds, outage tests,
> and the tests of `rewards` and `claims` (phase 2 services).

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
- `simulator` (not built yet; tests and load use the `core` factories meanwhile): generates trips for N drivers and injects faults (duplicate, out-of-order, late,
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

## Rewards tests (`integration-tests/rewards`)

Tests put `premium.adjusted` events straight on the topic (keyed by driver, built by `PremiumAdjustedFactory`), so each test
controls the band and score version. They read the rewards from `GET /v1/drivers/{driverId}/rewards` and what the provider
received from the mock. The provider is WireMock (`notification-mock`): `NotificationMockClient` (core) reads its journal and
injects failures through the admin API. Every stub and query is scoped to the test's driver ID in the request body, so
parallel tests never interfere, and each stub is removed in cleanup.

| Feature | Covers |
|---|---|
| Health | liveness and readiness (smoke) |
| Reward granting | excellent earns a reward and a notification (smoke); notification body, `Idempotency-Key` and correlation header; one reward per excellent score version; other bands earn nothing; a worse later band keeps the earned reward |
| Idempotency | duplicate event ID, same score version under a new event ID, late duplicate |
| Notification failures | provider 5xx or 429 retried 4 times then dead-lettered (reward stays pending); provider 4xx dead-lettered at once with one call; slow provider counts as down; recovery during retries ends notified; replay after recovery notifies the same reward; a failing reward does not block others |
| Rewards API | list by score version, empty list for an unknown driver, correlation ID |
| Dead letter and replay | unreadable, wrong type or version, invalid payloads, partition not blocked, log line without payload, replay |

Run: `./mvnw -pl integration-tests/rewards -am test -Dsuite=master -Denv=local` (needs `port-forward.sh`, which forwards `rewards` on 8085 and the mock on 8089).

## Claims tests (`integration-tests/claims`)

Tests put `driving.event` and `premium.adjusted` events straight on their topics (keyed by driver, built by `DrivingEventFactory`
and `PremiumAdjustedFactory`), so each test controls the crash, the policy and their order. The two topics have no order between
them, so every assertion on a claim is eventual: it waits for the status it expects (`opened` or `pending_policy`).

| Feature | Covers |
|---|---|
| Health | liveness and readiness (smoke) |
| Crash claims | crash opens a claim with trip evidence and policy version (smoke); crash before the policy waits as `pending_policy` then opens; later policy does not change an opened claim; severity kept; crash and policy at the same moment never leave a claim waiting; non-crash kinds open nothing; stale policy ignored; another driver's policy does not open a claim |
| Idempotency | duplicate event ID, same crash under a new event ID, duplicate while waiting for the policy, late duplicate |
| Claims API | list per driver, one claim by ID, correlation ID, empty list for an unknown driver (200), 404 for an unknown claim, 400 for a malformed ID |
| Dead letter and replay | unreadable events on both topics, wrong type or version, invalid crash payloads, partition not blocked, log line without payload, replay |

Run: `./mvnw -pl integration-tests/claims -am test -Dsuite=master -Denv=local` (needs `port-forward.sh`, which forwards `claims` on 8086).


## Gateway tests (`integration-tests/gateway`)

The gateway is a PoC. Only this module goes through it; every other module, e2e and load keep calling the services directly.
Tests log in as the users seeded by the gateway SQL script (all with the password `safedrive-poc`), so there is no setup.
`core` gained `GatewayClient`, `LoginFactory`, `Jwts` (reads and forges tokens: tampered, `alg: none`, another key, no signature),
the token, claims, error, public-key and log-line validators, and the `Scopes`, `GatewayUsers` and `GatewayMessages` constants.

| Feature | Covers |
|---|---|
| Health | liveness and readiness (smoke) |
| Token issuing | login gives a one hour RS256 token (smoke); claims (issuer, audience, user, driver from `X-Driver-Id`, scopes, life); scopes follow the user; public keys verify the token and hold no private part; wrong password, unknown user and disabled user get the same 401 (no user enumeration); SQL injection; missing or blank driver header, missing email, empty password, empty or malformed body give 400 |
| Token validation | a valid token reaches the service (smoke) and can be reused; no header, wrong scheme, empty bearer, garbage, tampered payload, another key, `alg: none`, no signature and wrong audience all give 401 |
| Scopes | each of the five scopes opens its routes and reaches the right service; a read-only user is 403 (naming the missing scope) on premium, rewards, claims, one claim and ingest; a refused batch is not forwarded (nothing on `trip.raw`); no ownership check, so a token for one driver reads another (documents the PoC) |
| Proxy | batch through the gateway is accepted and reaches `trip.raw` with the same correlation ID (smoke); status and body pass through (202, 200 duplicate, 400 invalid, malformed JSON); the correlation ID is kept or created; unknown path 404, actuator not exposed, service paths outside `/api` not served, wrong method 405, body over 1 MB is 413 |
| Logs | login, rejected login and proxied calls are logged with the correlation ID and without password, token or email |

Not covered: token expiry (needs a short TTL; unit tested in the service) and a service down or slow (502 and 504; unit tested).
Run: `./mvnw -pl integration-tests/gateway -am test -Dsuite=master -Denv=local` (needs `port-forward.sh`, which forwards `gateway` on 8087).

## E2E tests (`integration-tests/e2e`)

Tests drive only the edge (`POST /v1/telemetry/batches`) and follow one correlation ID across `trip.raw`,
`trip.completed`, `score.updated` and `premium.adjusted`, then read the score and premium APIs. The wait timeout is
60s (four hops). Same layout and suites as the service modules; classes sit under `com.qnt.safedrive.tests.e2e.<feature>`.

Factory trips are short (under 1 km), so scoring floors the distance at 1 km: a clean trip scores 100 (`excellent`,
96.00) and a trip with any driving event scores 0 (`high_risk`, 156.00). Two trips of one driver average to 50 (`poor`, 138.00).

| Feature | Covers |
|---|---|
| Happy path | a clean trip ends in score 100, `excellent` band and premium 96.00 (smoke) |
| Driver journey | speeding trip to `high_risk`; second trip averages and rates from the base premium (no compounding); two drivers do not affect each other; a batch rejected by ingest never enters the pipeline and the driver has no premium |
| Correlation | ingest creates the ID when the caller sends none and it reaches `premium.adjusted`; every service logs the ID and no log line carries the device ID or a position |
| Pipeline resilience | duplicate batch has no second effect; out-of-order batches give one result; a late speeding batch revises the trip (revision 2), recomputes the score (version 2) and moves the premium; a poison event is dead-lettered without blocking the chain |

Each case checks the exact number of events on every topic (watching a 5s window for extras). Correlation has no
negative class yet; outages (pod or Kafka down) and idle-timeout closing are still to do.

Run: `./mvnw -pl integration-tests/e2e -am test -Dsuite=master -Denv=local`.

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

## Reports and CI

- Every run writes Allure results to `target/allure-results` of the module. `AllureMetadataListener` (core, registered
  through `META-INF/services`, so no suite file lists it) adds `environment.properties`: environment name, endpoints,
  namespace, Kafka address, suite and, in GitHub Actions, the run, ref and commit. A new environment is a new
  `environment.<env>.json`; the report then shows which one it ran against.
- The HTML report is built by the tests themselves: `AllureReportListener` (core, same `META-INF/services` file) runs
  at the end of the TestNG execution, after every suite and `environment.properties` are written, and generates
  `target/allure-report/index.html` in-process with `allure-generator`; no CLI or extra Maven goal. It never fails the
  run. Serving it (`allure open`) is up to you.
- `@Step` works through the AspectJ weaver, loaded as a javaagent by surefire (`argLine` in the integration-tests
  parent pom). Every client, Kafka and log/script call in `core` is a step (see the testing rules); a failing call shows as the broken step.
- The generator needs Jackson 2. This is the one accepted exception to the Jackson rule: the Jackson 2 ban is lifted
  in `integration-tests` only (parent pom), and stays in force for `commons` and every service. Do not use
  `com.fasterxml.jackson` in test code; use `tools.jackson` through `Json`.
- CI (`.github/workflows/ci.yml`): `unit`, then `integration` on minikube with the real images and `deploy.sh`,
  `-Denv=ci`. The Allure reports (`target/allure-report` per module) and surefire output are uploaded as the `allure-reports-ci` artifact. Run it by hand from the
  Actions tab and pick `master`, `smoke` or `regression`.

## Load tests (`integration-tests/load`)

Gatling (Java DSL) simulations that reuse `core` (batch factory, IDs, paths, environment files), so the load sends
exactly what the functional tests send. Gatling brings Jackson 2; that is allowed in `integration-tests` (see
`.claude/rules/code-style.md`), and the load code itself still uses Jackson 3 through `core`.

| Simulation | What it does | Fails when |
|---|---|---|
| `IngestThroughputSimulation` | new drivers arrive at a steady rate, each posts one valid batch | any non-`202`, or p99 over the limit |
| `EndToEndLatencySimulation` | a driver posts a batch, then polls the premium API until it exists | a driver waits longer than the SLO (`-Dload.e2e.slo.ms`, default 30 s) or never gets a premium |
| `DuplicatesUnderLoadSimulation` | post (`202`), repeat (`200`), same batch ID with other content (`409`) | any other status, never a `5xx` |

- **Profiles:** `smoke` (2 drivers/s, 30 s), `baseline` (10/s, 2.5 min), `stress` (50/s, 3 min), chosen with
  `-Dload.profile`. The rates and the limits are provisional: run `baseline` on the target hardware, record the numbers,
  then pin the thresholds from them.
- **End-to-end latency:** Gatling cannot assert on a group duration (its group response time leaves out the pauses), so a
  late driver sends one deliberately failing "SLO breach" request. The real distribution is the group *duration* in the
  report ("ingest to premium" page).
- **How the load reaches the services** (`platform/scripts/load.sh <ingest|e2e|duplicates|all> [profile] --mode ...`):

| Mode | Path | Use |
|---|---|---|
| `incluster` (default) | Gatling pod calls the services by DNS, no host tunnel | the numbers to trust; CI default |
| `nodeport` | host process through a NodePort; on a Docker driver without a reachable node IP (macOS) through `minikube service --url`, which is a tunnel | outside-the-cluster client; direct (no tunnel) on a CI runner |
| `portforward` | `port-forward.sh` | dev and smoke only |

  Smoke ingest through the three paths (2 requests/s, 57 requests): p50 5 ms in-cluster, 11 ms port-forward, 47 ms
  through the minikube tunnel. The tunnel is not the services, so only `incluster` (or `nodeport` on a direct node IP) is
  a measurement of them.
- **Resources:** the generator shares the single minikube node with the services; the pod has fixed requests and
  limits (500m/512Mi, limit 1 CPU/1Gi). Absolute throughput is a local-hardware number: compare runs on the same
  hardware, and record the hardware with each baseline.
- **Reports:** Gatling HTML, `integration-tests/load/target/gatling-cluster/<run>/` (in-cluster) or `target/gatling/`. Watch
  Grafana during a run for lag, dead letters and publish rates.
- **CI:** `.github/workflows/load.yml`, manual, with inputs for simulation, profile and mode; reports are uploaded as
  the `gatling-<profile>-<mode>` artifact. It is not part of `ci.yml`.
- **Not done yet:** Toxiproxy (injected latency, jitter, bandwidth limits, dropped connections) in front of ingest; a
  Prometheus lag ceiling as an assertion; pinned thresholds.
