-- Runs as role "ingest" in schema "ingest" (created by platform/infra/10-postgres.yaml).
CREATE TABLE ingested_batches (
    batch_id       TEXT PRIMARY KEY,
    trip_id        TEXT        NOT NULL,
    driver_id      TEXT        NOT NULL,
    payload_hash   TEXT        NOT NULL,
    status         TEXT        NOT NULL,
    correlation_id TEXT        NOT NULL,
    received_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE outbox (
    id             BIGSERIAL PRIMARY KEY,
    event_id       UUID        NOT NULL UNIQUE,
    topic          TEXT        NOT NULL,
    msg_key        TEXT        NOT NULL,
    payload        TEXT        NOT NULL,
    correlation_id TEXT        NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    sent_at        TIMESTAMPTZ
);

CREATE INDEX outbox_unsent ON outbox (id) WHERE sent_at IS NULL;
