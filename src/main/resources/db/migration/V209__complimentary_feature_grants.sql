CREATE TABLE tenant_feature_grants (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
 tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
 feature_key VARCHAR(80) NOT NULL REFERENCES feature_catalog(feature_key),
 starts_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 expires_at TIMESTAMPTZ NOT NULL,
 revoked_at TIMESTAMPTZ,
 granted_by UUID NOT NULL REFERENCES app_users(id),
 created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 CONSTRAINT valid_grant_window CHECK (expires_at > starts_at)
);
CREATE INDEX idx_feature_grants_active ON tenant_feature_grants(tenant_id,feature_key,expires_at)
 WHERE revoked_at IS NULL;
