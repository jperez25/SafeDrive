# SafeDrive: Platform and deploy

> Status: DRAFT. Target: local minikube.

## Layout

```
platform/
  infra/      Kafka (single-node KRaft) and Postgres manifests
  services/   one deploy manifest per service
  scripts/    deploy.sh, replay-dlt.sh
```

## Infra

- **Kafka:** single-node KRaft StatefulSet, no Strimzi. Topics from `event-catalog.md` are created by a
  one-shot job (3 partitions, replication factor 1, plus the dead-letter topics).
- **Postgres:** one instance, one database `safedrive`. An init job creates one schema and one user per
  service (`ingest`, `trip`, `scoring`, `rating`); each user is granted only its own schema.
- Credentials come from Kubernetes Secrets built from a gitignored `.env`. Local dummy values only.

## Services

- One Deployment and Service per service, config through environment variables.
- Liveness and readiness probes point at the Actuator health endpoints.
- Each service runs its own Flyway migrations on its own schema at startup.

## deploy.sh

- `deploy.sh infra | <service> | all`, plus `down` and `status`.
- Order: Postgres and Kafka, then topics and schemas, then services. Each step waits for readiness
  (`kubectl rollout status`) before the next starts.
- `status` prints pod readiness and the health of each service.

## Later

Prometheus, Grafana and kafka-exporter (consumer lag), per `plan.md` section 6.
