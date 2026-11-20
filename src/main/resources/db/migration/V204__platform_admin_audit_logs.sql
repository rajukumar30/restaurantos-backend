-- Append-only audit history for privileged restaurant feature changes.
CREATE TABLE platform_admin_audit_logs (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
 actor_user_id UUID NOT NULL REFERENCES app_users(id),
 tenant_id UUID NOT NULL REFERENCES tenants(id),
 action VARCHAR(100) NOT NULL,
 feature_key VARCHAR(80) REFERENCES feature_catalog(feature_key),
 old_value BOOLEAN,
 new_value BOOLEAN,
 created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_platform_admin_audit_tenant_time
 ON platform_admin_audit_logs(tenant_id,created_at DESC);
REVOKE UPDATE, DELETE ON platform_admin_audit_logs FROM PUBLIC;
