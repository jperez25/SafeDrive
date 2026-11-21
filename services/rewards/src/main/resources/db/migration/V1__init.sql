-- Runs as role "rewards" in schema "rewards" (created by platform/infra/10-postgres.yaml).

-- One reward per driver and score version. The unique key is the idempotency guard:
-- duplicates of an event (same or new event ID) find the same row.
CREATE TABLE rewards (
    reward_id     UUID PRIMARY KEY,
    driver_id     TEXT        NOT NULL,
    score_version INT         NOT NULL,
    kind          TEXT        NOT NULL,
    status        TEXT        NOT NULL DEFAULT 'pending',
    attempts      INT         NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    notified_at   TIMESTAMPTZ,
    UNIQUE (driver_id, score_version)
);
