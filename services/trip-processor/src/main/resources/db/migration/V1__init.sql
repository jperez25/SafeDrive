-- Runs as role "trip" in schema "trip" (created by platform/infra/10-postgres.yaml).
CREATE TABLE trips (
    trip_id          TEXT PRIMARY KEY,
    driver_id        TEXT        NOT NULL,
    status           TEXT        NOT NULL DEFAULT 'OPEN',   -- OPEN | CLOSED
    revision         INT         NOT NULL DEFAULT 0,        -- number of trip.completed published
    last_seq         INT,                                   -- batchSeq of the batch flagged last
    correlation_id   TEXT        NOT NULL,
    last_activity_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    closed_at        TIMESTAMPTZ
);

-- One row per received batch; the key makes duplicate and replayed batches harmless.
CREATE TABLE samples (
    trip_id     TEXT        NOT NULL,
    batch_seq   INT         NOT NULL,
    data        TEXT        NOT NULL,                       -- JSON array of samples
    received_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (trip_id, batch_seq)
);

-- Events already published for a trip, so reprocessing never emits the same one twice.
CREATE TABLE driving_events (
    trip_id TEXT        NOT NULL,
    kind    TEXT        NOT NULL,
    ts      TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (trip_id, kind, ts)
);

CREATE TABLE processed_events (
    event_id     UUID PRIMARY KEY,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
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
CREATE INDEX trips_open ON trips (last_activity_at) WHERE status = 'OPEN';
