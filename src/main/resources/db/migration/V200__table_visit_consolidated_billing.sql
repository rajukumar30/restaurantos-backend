-- One open bill per table visit. Payments and WhatsApp delivery are deliberately
-- disabled until verified gateway and messaging integrations are configured.
CREATE TABLE table_visits (
 id UUID PRIMARY KEY,
 tenant_id UUID NOT NULL REFERENCES tenants(id),
 branch_id UUID NOT NULL REFERENCES branches(id),
 table_id UUID NOT NULL REFERENCES restaurant_tables(id),
 bill_number VARCHAR(40) NOT NULL UNIQUE,
 status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
 opened_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 closed_at TIMESTAMPTZ,
 CONSTRAINT table_visits_status_ck CHECK(status IN ('OPEN','PAYMENT_PENDING','PAID','CLOSED')),
 CONSTRAINT table_visits_closed_ck CHECK ((status='CLOSED') = (closed_at IS NOT NULL))
);
CREATE UNIQUE INDEX uq_table_one_active_visit ON table_visits(table_id)
 WHERE status IN ('OPEN','PAYMENT_PENDING','PAID');
ALTER TABLE restaurant_orders ADD COLUMN table_visit_id UUID REFERENCES table_visits(id);
CREATE INDEX idx_orders_table_visit ON restaurant_orders(table_visit_id);
CREATE TABLE table_bill_payments (
 id UUID PRIMARY KEY,
 tenant_id UUID NOT NULL REFERENCES tenants(id),
 visit_id UUID NOT NULL REFERENCES table_visits(id),
 provider VARCHAR(40) NOT NULL,
 provider_order_id VARCHAR(150),
 provider_payment_id VARCHAR(150),
 amount NUMERIC(12,2) NOT NULL CHECK(amount>=0),
 currency VARCHAR(3) NOT NULL DEFAULT 'INR',
 status VARCHAR(20) NOT NULL CHECK(status IN ('PENDING','PAID','FAILED')),
 created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 confirmed_at TIMESTAMPTZ,
 CONSTRAINT uq_bill_provider_payment UNIQUE(provider,provider_payment_id)
);
CREATE UNIQUE INDEX uq_one_pending_bill_payment ON table_bill_payments(visit_id) WHERE status='PENDING';
CREATE TABLE bill_delivery_outbox (
 id UUID PRIMARY KEY,
 tenant_id UUID NOT NULL REFERENCES tenants(id),
 visit_id UUID NOT NULL REFERENCES table_visits(id),
 destination_phone VARCHAR(13) NOT NULL,
 channel VARCHAR(20) NOT NULL DEFAULT 'WHATSAPP',
 status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
 attempts INTEGER NOT NULL DEFAULT 0,
 created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 sent_at TIMESTAMPTZ,
 UNIQUE(visit_id,channel),
 CONSTRAINT bill_delivery_status_ck CHECK(status IN ('PENDING','SENT','FAILED'))
);
