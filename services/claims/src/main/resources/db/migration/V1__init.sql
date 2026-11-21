-- Runs as role "claims" in schema "claims" (created by platform/infra/10-postgres.yaml).

-- The driver's current policy, copied from premium.adjusted (no service-to-service calls).
CREATE TABLE policies (
    driver_id     TEXT PRIMARY KEY,
    score_version INT            NOT NULL,
    base_premium  NUMERIC(12, 2) NOT NULL,
    new_premium   NUMERIC(12, 2) NOT NULL,
    band          TEXT           NOT NULL,
    updated_at    TIMESTAMPTZ    NOT NULL DEFAULT now()
);

-- One claim (first notice of loss) per crash. The unique key is the idempotency guard: a duplicate event, a
-- replay or the same crash under a new event ID finds the same row.
-- status: opened (policy known) | pending_policy (crash arrived before the policy).
CREATE TABLE claims (
    claim_id             UUID PRIMARY KEY,
    driver_id            TEXT             NOT NULL,
    trip_id              TEXT             NOT NULL,
    crash_ts             TIMESTAMPTZ      NOT NULL,
    severity             TEXT             NOT NULL,
    lat                  DOUBLE PRECISION NOT NULL,
    lon                  DOUBLE PRECISION NOT NULL,
    correlation_id       TEXT,
    status               TEXT             NOT NULL,
    policy_score_version INT,
    created_at           TIMESTAMPTZ      NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ      NOT NULL DEFAULT now(),
    UNIQUE (trip_id, crash_ts)
);
CREATE INDEX claims_driver_idx ON claims (driver_id, created_at DESC);
