# SafeDrive

A mini telematics insurance platform: six small event-driven microservices, and a serious test platform around them.
Telemetry goes in at the edge; a risk score, a premium adjustment, a reward and, after a crash, an insurance claim come out.

**The test platform is the main deliverable.** The services stay thin on purpose, so there is something realistic to test:
duplicates, out-of-order and late data, poison events, retries, eventual consistency and cross-service flows.

> Open [`docs/platform-overview.html`](docs/platform-overview.html) in a browser for a diagram of the platform and the event flow.

## Architecture

```
client -> telemetry-ingest -> trip.raw -> trip-processor -> trip.completed -> scoring -> score.updated -> policy-rating -> premium.adjusted -> rewards -> notification provider (mock)
                                                  |                                                                            |
                                                  +-- driving.event ------------------------------------> claims <-- premium.adjusted
```

| Service | Job | Port |
|---|---|---|
| `telemetry-ingest` | HTTP edge: validate, dedupe, assign correlation ID, publish `trip.raw` | 8081 |
| `trip-processor` | Assemble trips (late and out-of-order batches), detect driving events and crashes | 8082 |
| `scoring` | Rolling driver risk score | 8083 |
| `policy-rating` | Score band to premium or discount | 8084 |
| `rewards` | Safe-driver reward and notification through an external provider | 8085 |
| `claims` | Open a claim per crash, keep a copy of the driver's policy | 8086 |

Principles (full list in `.claude/rules/architecture.md`):
- Services talk through Kafka; HTTP only at the edges (ingest and read APIs).
- At-least-once delivery: unique event IDs, idempotent consumers, transactional outbox.
- One Postgres schema and one DB user per service; no cross-schema joins.
- A correlation ID is created at the edge and travels through every event, log line and HTTP call.
- Unprocessable events go to `<topic>.<group>.dlt` and can be replayed.

## Stack

Java 25, Spring Boot 4.x, Jackson 3, Maven multi-module (wrapper included), Kafka (KRaft), Postgres, minikube,
TestNG, REST Assured, Allure, Gatling, WireMock. Root package `com.qnt.safedrive`.

## Getting started

Prerequisites: JDK 25 (Corretto), Docker (or Colima), `minikube`, `kubectl`. No system Maven needed.

```bash
# 1. Configure (local dummy values only; .env is gitignored)
#    create .env with the Postgres credentials expected by platform/scripts/deploy.sh

# 2. Build and run the unit and contract tests
./mvnw clean install

# 3. Deploy Kafka, Postgres, the services and monitoring to minikube
platform/scripts/deploy.sh all
platform/scripts/deploy.sh status

# 4. Expose services, Kafka and Postgres on localhost (keep running, Ctrl-C stops it)
platform/scripts/port-forward.sh
```

Other deploy commands: `deploy.sh infra | <service> | monitoring | down`.
Grafana (`SafeDrive pipeline` dashboard) is on `localhost:3000`, Prometheus on `localhost:9090`.

## Testing

Black-box tests drive the system only through its HTTP APIs, Kafka topics and logs. They live in `integration-tests/`:
`core` (shared models, clients, factories, validators), one module per service, `e2e` (ingest to premium and claim) and `load`.

```bash
./mvnw test                                                                  # unit + event contract tests
./mvnw -pl integration-tests/scoring -am test -Dsuite=smoke -Denv=local     # one service, fast checks
./mvnw -pl integration-tests/e2e -am test -Dsuite=master -Denv=local        # smoke, then regression, full flow
platform/scripts/load.sh all smoke                                           # Gatling: ingest, e2e latency, duplicates
platform/scripts/replay-dlt.sh <topic>.<group>.dlt                           # replay dead letters
```

- Suites: `smoke` (is it up, does the main path work), `regression` (full positive and negative set), `master` (smoke gates regression).
- Every run writes an Allure HTML report to `target/allure-report/index.html` of the module.
- CI (`.github/workflows/ci.yml`) runs unit tests, then the integration suites on minikube; `load.yml` runs load tests on demand.
- Failure modes covered: duplicate and concurrent duplicate batches, out-of-order and late data, poison events, dead-letter and replay, eventual consistency. Still to do: outages (pod or Kafka down), Toxiproxy, pinned load thresholds, the `simulator`.

## Repository layout

```
commons/            shared lib: event envelope, correlation ID, config (no business logic)
services/           telemetry-ingest, trip-processor, scoring, policy-rating, rewards, claims
integration-tests/  core, one module per service, e2e, load
docs/               contracts (JSON Schema events), architecture, design
platform/           infra (Kafka, Postgres), services (manifests), monitoring, load, scripts
simulator/          trip and fault generator (not built yet)
.claude/            project rules and skills
```

## Documentation

- [`docs/design/plan.md`](docs/design/plan.md): the approved plan and open decisions.
- [`docs/design/status.md`](docs/design/status.md): what is built.
- [`docs/architecture/services.md`](docs/architecture/services.md): features and design per service.
- [`docs/architecture/event-catalog.md`](docs/architecture/event-catalog.md): envelope, topics, payloads, delivery rules.
- [`docs/architecture/test-platform.md`](docs/architecture/test-platform.md): test modules, suites, reports, CI, load.
- [`docs/architecture/platform.md`](docs/architecture/platform.md): minikube deploy and monitoring.
- [`docs/contracts/`](docs/contracts): event JSON Schemas and the notification provider contract.

## Conventions

- Commit messages start with the ticket key: `QNTA-<n>: short description`. Commits and pushes need explicit confirmation (`.claude/rules/git.md`).
- Jackson 3 only (`tools.jackson.*`); the one exception is `integration-tests`, where report and load tooling bring Jackson 2.
- Config through environment variables; never commit `.env` or secrets; no secrets or PII in logs.
- Keep services thin and code simple. See `.claude/rules/` for the full rules.
