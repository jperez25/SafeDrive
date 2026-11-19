-- Runs as role "scoring" in schema "scoring" (created by platform/infra/10-postgres.yaml).

-- Latest accepted revision of every trip and the score it earned. A newer revision replaces the row.
CREATE TABLE trip_scores (
    trip_id    TEXT PRIMARY KEY,
    driver_id  TEXT             NOT NULL,
    revision   INT              NOT NULL,
    trip_score DOUBLE PRECISION NOT NULL,
    ended_at   TIMESTAMPTZ      NOT NULL,
    updated_at TIMESTAMPTZ      NOT NULL DEFAULT now()
);

-- Current score per driver; score_version is the monotonic version published in score.updated.
CREATE TABLE driver_scores (
    driver_id      TEXT PRIMARY KEY,
    score_version  INT              NOT NULL DEFAULT 0,
    score          DOUBLE PRECISION NOT NULL DEFAULT 0,
    based_on_trips INT              NOT NULL DEFAULT 0,
    computed_at    TIMESTAMPTZ      NOT NULL DEFAULT now()
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
CREATE INDEX trip_scores_driver_recent ON trip_scores (driver_id, ended_at DESC, trip_id);
