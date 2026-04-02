-- ============================================================
-- V1 — Initial ZTE schema bootstrap
-- ============================================================
-- Gateway audit log: immutable append-only record of every
-- authenticated request that passed through the gateway.
-- Kept minimal for MVP; extend with partitioning at scale.
-- ============================================================

CREATE TABLE IF NOT EXISTS gateway_audit_log (
    id           BIGSERIAL    PRIMARY KEY,
    subject      TEXT         NOT NULL,                -- JWT sub claim
    client_id    TEXT,                                 -- JWT azp (authorized party)
    http_method  VARCHAR(10)  NOT NULL,
    path         TEXT         NOT NULL,
    status_code  SMALLINT,
    duration_ms  INTEGER,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_subject    ON gateway_audit_log (subject);
CREATE INDEX idx_audit_created_at ON gateway_audit_log (created_at DESC);

COMMENT ON TABLE gateway_audit_log IS
    'Immutable audit trail of authenticated requests through the ZTE gateway.';
