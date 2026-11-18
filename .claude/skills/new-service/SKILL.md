---
name: new-service
description: Scaffold a new SafeDrive microservice module (Maven module, health endpoints, Kafka config, deploy manifest, test dir). Use when adding a service under services/.
---
# New service
Args: service name (kebab-case), e.g. `rewards`.

1. Create `services/<name>/` with a Maven module (placeholder parent `com.qnt.safedrive:safedrive-parent:0.1.0`) and add it to the root pom modules.
2. Add a Spring Boot 4.x main class (Jackson 3 only, see `.claude/rules/code-style.md`), Actuator health and readiness endpoints, and correlation ID filter from `commons`.
3. Add Kafka consumer/producer config using the shared event envelope; make consumers idempotent on `eventId`.
4. Add a Flyway migration dir and a dedicated Postgres schema and user `<name>` (grants only on that schema) (dummy password `changeme`).
5. Add `platform/services/<name>/` deploy manifests and register it in `deploy.sh`.
6. Create `integration-tests/<name>/` and a suite XML in `integration-tests/suites/`.
7. Add the service to `docs/architecture/` and follow `.claude/rules/architecture.md`.
