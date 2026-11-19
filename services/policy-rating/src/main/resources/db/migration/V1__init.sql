-- Runs as role "rating" in schema "rating" (created by platform/infra/10-postgres.yaml).

-- One policy per driver, created on the first score. The current rating lives here.
CREATE TABLE policies (
    driver_id      TEXT PRIMARY KEY,
    base_premium   NUMERIC(10, 2)   NOT NULL,
    score_version  INT              NOT NULL DEFAULT 0,
    adjustment_pct DOUBLE PRECISION NOT NULL DEFAULT 0,
    new_premium    NUMERIC(10, 2)   NOT NULL,
    band           TEXT             NOT NULL,
    updated_at     TIMESTAMPTZ      NOT NULL DEFAULT now()
);

-- History of applied scores. The unique key makes one score version apply at most once.
CREATE TABLE premium_adjustments (
    id             BIGSERIAL PRIMARY KEY,
    driver_id      TEXT             NOT NULL,
    score_version  INT              NOT NULL,
    score          DOUBLE PRECISION NOT NULL,
    band           TEXT             NOT NULL,
    adjustment_pct DOUBLE PRECISION NOT NULL,
    new_premium    NUMERIC(10, 2)   NOT NULL,
    created_at     TIMESTAMPTZ      NOT NULL DEFAULT now(),
    UNIQUE (driver_id, score_version)
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
