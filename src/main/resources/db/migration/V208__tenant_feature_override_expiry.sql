-- Optional expiry for restaurant-level feature overrides. Expired overrides are ignored.
ALTER TABLE tenant_feature_overrides
  ADD COLUMN expires_at TIMESTAMPTZ NULL;
CREATE INDEX idx_tenant_feature_override_expiry
  ON tenant_feature_overrides(expires_at) WHERE expires_at IS NOT NULL;
