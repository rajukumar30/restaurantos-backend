-- Demo customer phone verification; preserve all earlier Flyway versions.
CREATE TABLE IF NOT EXISTS guest_otp_challenges (
 id UUID PRIMARY KEY,
 tenant_id UUID NOT NULL REFERENCES tenants(id),
 phone VARCHAR(13) NOT NULL,
 expires_at TIMESTAMPTZ NOT NULL,
 attempts INTEGER NOT NULL DEFAULT 0,
 consumed_at TIMESTAMPTZ,
 created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_guest_otp_limits ON guest_otp_challenges(tenant_id,phone,created_at DESC);
CREATE TABLE IF NOT EXISTS guest_customer_sessions (
 token_hash CHAR(64) PRIMARY KEY,
 tenant_id UUID NOT NULL REFERENCES tenants(id),
 phone VARCHAR(13) NOT NULL,
 expires_at TIMESTAMPTZ NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_guest_sessions_expiry ON guest_customer_sessions(expires_at);
