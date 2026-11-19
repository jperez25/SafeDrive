# SafeDrive: Status

Last updated: 2026-11-19. Phases are defined in `plan.md` section 8.

| # | Phase | Status | Summary |
|---|---|---|---|
| 1 | Foundations | Done | Maven wrapper and parent pom (Java 25, Spring Boot 4.1.2, Jackson 3 enforced). `commons` with event envelope, correlation ID and env config. JSON Schema contracts for the envelope and 5 events, with examples and 23 contract tests. Infra on minikube (Colima): Postgres (schema and user per service), single-node KRaft Kafka, 9 topics, `deploy.sh`. Verified on a real cluster. |
| 2 | Vertical slice | Done, testing in progress | 2.1 `telemetry-ingest` (QNTA-3). 2.2 `trip-processor`: assembles out-of-order and late batches (revisions), detects 4 event kinds, outbox to `trip.completed` and `driving.event`, dead-letter plus `replay-dlt.sh`, 16 unit tests, verified on minikube through the real ingest API. `integration-tests/core` is built (see below); black-box tests for both services come next. |
| 3 | Scoring and rating | Not started | `scoring` and `policy-rating`, plus the e2e flow. |
| 4 | Failure-mode tests | Not started | Duplicates, out-of-order, late data, outages, dead-letter and replay. |
| 5 | Observability, load, CI | Not started | Prometheus, Grafana, kafka-exporter, load tests, GitHub Actions. |
| 6 | Phase 2 services | Not started | `rewards` and `claims` (not designed in detail yet). |

## Other work

| Area | Status | Notes |
|---|---|---|
| Design docs | Done | `plan.md` (approved), `architecture/services.md`, `event-catalog.md`, `platform.md`. |
| Test platform | Drafted, parked | `architecture/test-platform.md`. Implementation waits until the app side is done. |
| `simulator` | Not started | Needed for the failure-mode tests. |
| `integration-tests/core` | Done | Lombok models (`@With` + `@Builder`), `BatchFactory`, REST clients, `Waiter`, topic/log tracer (`kubectl`, no DB), validators with children (contract schema check), `AbstractTest`. 23 unit tests. Not yet run against the cluster. |
| `integration-tests/telemetry-ingest` | Done | Fluent validators (`Validator.with(...)`, master and child), constants by concept, 74 tests passing on minikube: health, batch ingest (positive and negative), deduplication (sequential and concurrent), sample validation (limits and partial rejection). `SmokeGateListener` skips regression when smoke fails; `platform/scripts/port-forward.sh` exposes services, Postgres and Kafka. Resilience tests (Kafka or pod down) later. |

## Open items

- Which module owns the mocked notification provider for `rewards` (phase 6).
