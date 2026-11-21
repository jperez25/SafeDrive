# SafeDrive: Platform and deploy

> Status: DRAFT. Target: local minikube.

## Layout

```
platform/
  infra/      Kafka (single-node KRaft) and Postgres manifests
  services/   one deploy manifest per service
  scripts/    deploy.sh, replay-dlt.sh, port-forward.sh, load.sh
  load/       load pod and NodePort manifests used by load.sh
```

## Infra

- **Kafka:** single-node KRaft StatefulSet, no Strimzi. Topics from `event-catalog.md` are created by a
  one-shot job (3 partitions, replication factor 1, plus the dead-letter topics).
  A Postgres volume created before a service existed has no schema or user for it: create them once by hand with the
  statements of `create_service` in `platform/infra/10-postgres.yaml` (or `deploy.sh down` and redeploy, which wipes the data).
- **Postgres:** one instance, one database `safedrive`. An init job creates one schema and one user per
  service (`ingest`, `trip`, `scoring`, `rating`, `rewards`, `claims`); each user is granted only its own schema.
- Credentials come from Kubernetes Secrets built from a gitignored `.env`. Local dummy values only.

## Services

- `notification-mock` is not a SafeDrive service: a WireMock deployment (`platform/services/notification-mock`) that plays the external
  notification provider for `rewards` (default stub: `202`). It is deployed before `rewards` and has no Dockerfile or database.

- One Deployment and Service per service, config through environment variables.
- Liveness and readiness probes point at the Actuator health endpoints.
- Each service runs its own Flyway migrations on its own schema at startup.

## deploy.sh

- `deploy.sh infra | <service> | all`, plus `down` and `status`.
- Order: Postgres and Kafka, then topics and schemas, then services. Each step waits for readiness
  (`kubectl rollout status`) before the next starts.
- `status` prints pod readiness and the health of each service.

## port-forward.sh

- Forwards `telemetry-ingest` (8081), `trip-processor` (8082), `scoring` (8083), `policy-rating` (8084), `rewards` (8085), `claims` (8086), `notification-mock` (8089),
  Postgres (5432) and Kafka (9094) to localhost; runs in the foreground, Ctrl-C stops all. Services that are
  not deployed are skipped. Override ports with `INGEST_PORT`, `TRIP_PORT`, `SCORING_PORT`, `RATING_PORT`,
  `POSTGRES_PORT`, `GRAFANA_PORT`, `PROMETHEUS_PORT`. The Kafka port is fixed at 9094.
- Kafka has two client listeners: `PLAINTEXT` (9092, in-cluster, used by the services) and `EXTERNAL`, advertised as
  `localhost:9094` and reached through the forward, so the tests use a Kafka client directly on the host.

## Monitoring

- `deploy.sh monitoring` (also part of `all`) deploys kafka-exporter, Prometheus (static scrape targets, 6h retention) and
  Grafana (anonymous viewer, provisioned datasource and the `SafeDrive pipeline` dashboard from `platform/monitoring/dashboards/`).
- Every service exposes `/actuator/prometheus`. Business metrics: `safedrive_ingest_batches_total{status}` (ingest),
  `safedrive_events_published_total{topic}` (every service, counted when the outbox relay publishes) and
  `safedrive_events_dead_lettered_total{topic}` (the three consumers). Consumer lag comes from kafka-exporter
  (`kafka_consumergroup_lag`).
- `port-forward.sh` also forwards Grafana (3000) and Prometheus (9090).
