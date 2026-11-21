CREATE TABLE IF NOT EXISTS customer_feedback (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL REFERENCES tenants(id), order_id UUID,
 customer_name VARCHAR(120), rating INTEGER NOT NULL CHECK (rating BETWEEN 1 AND 5),
 comment VARCHAR(2000), owner_reply VARCHAR(1000), created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_customer_feedback_tenant ON customer_feedback(tenant_id,created_at DESC);
CREATE TABLE IF NOT EXISTS restaurant_promotions (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL REFERENCES tenants(id),
 code VARCHAR(40) NOT NULL, description VARCHAR(500),
 discount_type VARCHAR(10) NOT NULL CHECK(discount_type IN ('PERCENT','FIXED')),
 discount_value NUMERIC(12,2) NOT NULL CHECK(discount_value > 0),
 minimum_order_amount NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK(minimum_order_amount >= 0),
 starts_at TIMESTAMPTZ NOT NULL, ends_at TIMESTAMPTZ NOT NULL,
 usage_limit INTEGER CHECK(usage_limit > 0), usage_count INTEGER NOT NULL DEFAULT 0,
 active BOOLEAN NOT NULL DEFAULT TRUE, created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 CONSTRAINT promotion_dates CHECK(ends_at > starts_at),
 CONSTRAINT promotion_percent CHECK(discount_type <> 'PERCENT' OR discount_value <= 100),
 CONSTRAINT promotion_unique_code UNIQUE(tenant_id,code)
);
CREATE INDEX IF NOT EXISTS idx_promotions_tenant ON restaurant_promotions(tenant_id,created_at DESC);
