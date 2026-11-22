-- Runs as role "gateway" in schema "gateway" (created by platform/infra/10-postgres.yaml).

-- PoC: a plain user table. Registration lives in another service later; users are seeded (V2).
CREATE TABLE users (
    user_id       UUID PRIMARY KEY,
    email         TEXT        NOT NULL UNIQUE,
    password_hash TEXT        NOT NULL,
    scopes        TEXT        NOT NULL,   -- space separated, e.g. 'score:read premium:read'
    status        TEXT        NOT NULL DEFAULT 'active',   -- active | disabled
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
