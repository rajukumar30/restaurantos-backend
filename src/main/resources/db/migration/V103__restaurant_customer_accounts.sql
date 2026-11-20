CREATE TABLE IF NOT EXISTS restaurant_customers (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
 phone_e164 VARCHAR(20) NOT NULL UNIQUE,
 full_name VARCHAR(120) NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
ALTER TABLE restaurant_orders ADD COLUMN IF NOT EXISTS customer_id UUID REFERENCES restaurant_customers(id);
CREATE INDEX IF NOT EXISTS idx_restaurant_orders_customer ON restaurant_orders(customer_id,created_at DESC);
