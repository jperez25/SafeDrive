# Architecture rules
- One service, one job. No shared tables: one schema and one DB user per service in a single Postgres database. A user only has grants on its own schema; no cross-schema joins or foreign keys.
- Services talk through Kafka (async). HTTP only at the edges (ingest, read APIs).
- Delivery is at-least-once: every event has a unique event ID and consumers are idempotent.
- A correlation ID is created at the edge and propagated through every event, log line and HTTP call.
- `commons` holds only the envelope, correlation ID and config. Never put business logic there.
- Every service exposes health and readiness endpoints.
- Unprocessable events go to a dead-letter topic and must be replayable.
