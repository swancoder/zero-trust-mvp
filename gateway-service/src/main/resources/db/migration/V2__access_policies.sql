-- ============================================================
-- V2 — ZTE Access Policy table
-- ============================================================
-- Stores the DB-driven authorization rules evaluated by the
-- ZteAuthorizationFilter on every authenticated request.
--
-- Policy evaluation: a request is ALLOWED when at least one
-- enabled row matches ALL of: role_name ∈ JWT realm_access.roles,
-- path_pattern (Ant-style) matches the request path, and
-- methods (comma-separated or '*') includes the HTTP method.
-- ============================================================

CREATE TABLE IF NOT EXISTS access_policies (
    id           BIGSERIAL    PRIMARY KEY,
    role_name    VARCHAR(100) NOT NULL,
    path_pattern VARCHAR(255) NOT NULL,
    methods      VARCHAR(255) NOT NULL DEFAULT '*',
    enabled      BOOLEAN      NOT NULL DEFAULT true,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_access_policies_role    ON access_policies (role_name);
CREATE INDEX idx_access_policies_enabled ON access_policies (enabled);

COMMENT ON TABLE  access_policies              IS 'DB-driven Zero Trust access policy rules evaluated per-request by the gateway.';
COMMENT ON COLUMN access_policies.role_name    IS 'Keycloak realm role (from realm_access.roles JWT claim).';
COMMENT ON COLUMN access_policies.path_pattern IS 'Ant-style path pattern (e.g. /api/v1/service-a/**).';
COMMENT ON COLUMN access_policies.methods      IS 'Comma-separated HTTP methods or * for all (e.g. GET,POST).';
COMMENT ON COLUMN access_policies.enabled      IS 'Soft-disable a rule without deleting it.';

-- Default seed: ADMIN role can access service-a (GET and POST)
INSERT INTO access_policies (role_name, path_pattern, methods)
VALUES ('ADMIN', '/api/v1/service-a/**', 'GET,POST');
