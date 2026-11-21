# SafeDrive: Plan

> Status: APPROVED. Phases 1 to 5 are built; phase 6 (`rewards`, `claims`) is in progress. Live progress per phase: `status.md`.

## 1. Goal

A mini telematics insurance platform built as event-driven microservices, plus a serious
test platform around it. The business mirrors Quanata's core: context-based auto insurance
(real-time telematics, risk scoring, risk-based pricing).

The test platform is the main deliverable. The services stay deliberately thin.

## 2. Architecture

- **Monorepo, multi-module.** One git repo, one module per service.
- **One service, one job.** Each service does a single thing.
- **Async communication** through one shared Kafka cluster. Edges (ingest, read APIs) are HTTP.
- **Schema per service.** No shared tables. One Postgres instance and one database locally, one schema and one DB user per service. Each user only has grants on its own schema; no cross-schema joins or foreign keys.
- **At-least-once delivery, idempotent consumers.** Every event has a unique event ID.
- **Correlation ID** is created at the edge and travels through every event, log line and HTTP call.

### Services (core pipeline)

| Service | Responsibility | DB |
|---|---|---|
| `telemetry-ingest` | Entry point. Accept trip batches over HTTP, validate, dedupe, publish raw events | yes (dedup, outbox) |
| `trip-processor` | Turn raw samples into trips and driving events | yes |
| `scoring` | Compute and expose a driver risk score from trips | yes |
| `policy-rating` | Adjust premium/discount from score | yes |

Phase 2 (after core is stable): `rewards`, `claims`. See `docs/architecture/services.md`.

### Other modules

| Module | Purpose |
|---|---|
| `commons` | Small shared library: event envelope, correlation ID, common config. No business logic. |
| `docs` | `design/` (plan), `architecture/` (architecture notes, event catalog), `contracts/` (event schemas, API specs). |
| `platform` | Deploy scripts and infra manifests (Kafka, Postgres, per-service deploy). |
| `simulator` | Tool that generates realistic trips and injects faults. Not a service. |
| `integration-tests` | Black-box tests. Own module with `core`, one sub-module per service, and `e2e`. |

## 3. Repo layout

```
SafeDrive/
  commons/
  docs/
    design/         plan.md
    architecture/   services (features and design), event catalog, platform, test platform
    contracts/      event schemas, API specs
  platform/{infra,services,scripts}/
  services/{telemetry-ingest,trip-processor,scoring,policy-rating}/
  simulator/
  integration-tests/
    core/        shared models, clients, factories, validators, waiter
    <service>/   tests for one service, organized by feature
                 src/test/resources/environment.<env>.json, suites/{master,regression,smoke}.xml
    e2e/         full end-to-end flow, from ingest to premium
```

## 4. Deployment

- A master script: `deploy.sh all | <service> | infra`, plus `down` and `status`.
- Order: infra (Kafka, Postgres) first, then the services. Each service waits for readiness.
- Target: local minikube. Same pattern as QuickMoney.
- Every service exposes health and readiness endpoints.

## 5. Testing

- **Unit tests** live inside each service module (and `commons`). Contract tests (schema and
  Pact) are unit-level and live there too.
- **Integration tests** live in `integration-tests`, black-box, one sub-module per service.
  Inside a service, tests are grouped by feature, with a positive and a negative test class per
  feature, both extending an abstract class for that feature (abstract test pattern).
- **`core`** holds everything reusable: models, clients, factories, validators, waiter.
- **`e2e`** is the one special module: it checks the full flow end to end.
- **Resources:** each service module has `src/test/resources/` with `environment.<env>.json` files
  (base URLs, topics, timeouts per environment) and a `suites/` dir.
- **Suites:** each service has `master.xml`, `regression.xml` and `smoke.xml`. `master.xml` calls the other two.
- Run by module and suite, e.g. `-pl integration-tests/<module> -Dsuite=smoke -Denv=<env>`.
- Follow the QuickMoney conventions: TestNG, REST Assured, Allure, `AbstractTest`, fluent validators (master
  with child validators), factories with `withX`, constants, atomic and parallel-safe tests,
  best-effort cleanup.
- Failure-mode coverage: duplicates, out-of-order, late data, service down mid-pipeline,
  dead-letter queue, replay, eventual consistency.

## 6. Observability

- Phase 1: structured JSON logs with correlation ID, Spring Boot Actuator health.
- Phase 2: Micrometer, Prometheus, Grafana, kafka-exporter (consumer lag).
- Later, optional: OpenTelemetry tracing (Jaeger or Tempo).

## 7. Tech stack (proposed)

Java 25 (Corretto), Spring Boot 4.x with Jackson 3 only, Maven multi-module, Kafka, Postgres, Docker, minikube, TestNG, REST Assured,
Allure, Pact (contracts, unit-level), k6 or Gatling (load), GitHub Actions (CI).

## 8. Phases

1. Foundations: parent pom, `commons`, event envelope, contracts, deploy of infra. **Done.**
2. Vertical slice: `telemetry-ingest` → `trip-processor` with health endpoints and first tests. **Done.**
3. `scoring` and `policy-rating`, e2e flow. **Done.**
4. Failure-mode tests: duplicates, out-of-order, late data, poison events, dead-letter and replay are covered per service
   and in `e2e`. **Partly done:** outages (pod or Kafka down) and idle-timeout closing are still to do; the `simulator` is not built.
5. Observability, load tests, CI. **Done** (provisional load thresholds, Toxiproxy still to do).
6. Phase 2 services: `rewards` (done), then `claims` (design written, `trip-processor` crash detection built, service next).

## 9. Decisions

Decided:
- Event format: JSON Schema. Avro with a schema registry is the fallback if we need performance.
- `telemetry-ingest` is the platform entry point and keeps a small DB for dedup, race handling and outbox.
- Kafka locally: single-node KRaft manifest (no Strimzi).
- Topics, envelope and per-service design: `docs/architecture/event-catalog.md` and `docs/architecture/services.md`.
- Build tool and runtime: Maven, Java 25 (Corretto), Spring Boot 4.x, Jackson 3 only.
- Repo name `SafeDrive`, root package `com.qnt.safedrive`.
- Postgres: one database, one schema and one user per service.
- Phase 2: `rewards` and `claims` consume `premium.adjusted`; `claims` detects crashes from `driving.event` (`kind: crash`). No service-to-service HTTP.
- Test tracing helper uses logs and topics only, no DB reads.

- `rewards` notification provider: a WireMock deployment under `platform/services/notification-mock` (no code, tests read the received calls and inject failures through its admin API).

Nothing else is open on the app side. The test platform is described in `docs/architecture/test-platform.md`.
