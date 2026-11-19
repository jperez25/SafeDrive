# SafeDrive

Mini telematics insurance platform: event-driven microservices plus a serious test platform.
The test platform is the main deliverable; services stay thin. See `docs/design/plan.md` and
`docs/architecture/services.md` (features and design per service).

## Layout
- `commons/` shared lib (event envelope, correlation ID, config). No business logic.
- `services/{telemetry-ingest,trip-processor,scoring,policy-rating}/` one service, one job.
- `integration-tests/` black-box tests (`core` shared models/clients/validators, one dir per service, `e2e` full end-to-end flow).
- `docs/{contracts,architecture,design}/`, `platform/{infra,services,scripts}/`, `simulator/`.

## Stack (placeholder values, pending open decisions in plan.md section 9)
Java 25, Spring Boot 4.x, Maven multi-module, Kafka, Postgres, minikube,
TestNG, REST Assured, Allure, Pact. Root package: `com.qnt.safedrive`.

## Commands (placeholders)
- Build: `./mvnw clean install` (Maven wrapper, no system Maven needed)
- Unit tests: `./mvnw test`
- Integration tests: `./mvnw -pl integration-tests/<module> -am test -Dsuite=smoke -Denv=<env>` (`-am` builds `core` first; modules: telemetry-ingest, trip-processor, scoring, policy-rating; needs `platform/scripts/port-forward.sh`)
- Deploy: `platform/scripts/deploy.sh all|<service>|infra`, plus `down` and `status`
- Replay dead letters: `platform/scripts/replay-dlt.sh <topic>.<group>.dlt`

## Rules
Detailed rules are in `.claude/rules/`. Skills are in `.claude/skills/`.
