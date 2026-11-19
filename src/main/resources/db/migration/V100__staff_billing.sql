CREATE TABLE IF NOT EXISTS restaurant_order_payments (
 id UUID PRIMARY KEY,
 tenant_id UUID NOT NULL REFERENCES tenants(id),
 branch_id UUID NOT NULL REFERENCES branches(id),
 order_id UUID NOT NULL REFERENCES restaurant_orders(id),
 amount NUMERIC(12,2) NOT NULL CHECK (amount > 0),
 method VARCHAR(20) NOT NULL CHECK (method IN ('CASH','UPI','CARD','OTHER')),
 reference VARCHAR(120),
 recorded_by UUID NOT NULL REFERENCES app_users(id),
 created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 CONSTRAINT restaurant_order_payment_unique_reference UNIQUE(tenant_id,branch_id,method,reference)
);
CREATE INDEX IF NOT EXISTS idx_order_payments_order ON restaurant_order_payments(tenant_id,branch_id,order_id);
