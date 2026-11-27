-- Branch-owned UPI destination. Freeze the destination and amount when checkout begins.
CREATE TABLE branch_checkout_settings (
 tenant_id UUID NOT NULL REFERENCES tenants(id),branch_id UUID NOT NULL REFERENCES branches(id),
 upi_id VARCHAR(150),merchant_name VARCHAR(100),updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 PRIMARY KEY(tenant_id,branch_id)
);
ALTER TABLE table_visits ADD COLUMN checkout_amount NUMERIC(12,2),ADD COLUMN checkout_upi_id VARCHAR(150),ADD COLUMN checkout_merchant_name VARCHAR(100),ADD COLUMN checkout_requested_at TIMESTAMPTZ;
CREATE UNIQUE INDEX uq_manual_bill_reference ON table_bill_payments(tenant_id,provider,provider_order_id) WHERE provider LIKE 'MANUAL_%' AND provider_order_id IS NOT NULL;
CREATE INDEX idx_checkout_branch_history ON table_visits(tenant_id,branch_id,opened_at DESC,id);
