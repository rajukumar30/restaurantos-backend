-- Opaque, expiring receipt links. Never expose internal invoice IDs as authorization.
CREATE TABLE bill_receipt_links (
 token_hash CHAR(64) PRIMARY KEY,
 tenant_id UUID NOT NULL REFERENCES tenants(id),
 visit_id UUID NOT NULL REFERENCES table_visits(id),
 expires_at TIMESTAMPTZ NOT NULL,
 revoked_at TIMESTAMPTZ,
 created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 CONSTRAINT uq_receipt_visit UNIQUE(visit_id)
);
CREATE INDEX idx_receipt_expiry ON bill_receipt_links(expires_at);
-- Outbox messages contain only an opaque URL; no PDF attachments or binary payloads.
ALTER TABLE bill_delivery_outbox ADD COLUMN receipt_url TEXT;
ALTER TABLE bill_delivery_outbox ADD COLUMN provider_message_id VARCHAR(200);
ALTER TABLE bill_delivery_outbox ADD COLUMN last_error TEXT;
