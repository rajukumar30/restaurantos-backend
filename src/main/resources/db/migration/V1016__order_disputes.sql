CREATE TABLE order_disputes(
 id UUID PRIMARY KEY,tenant_id UUID NOT NULL REFERENCES tenants(id),branch_id UUID NOT NULL REFERENCES branches(id),
 visit_id UUID NOT NULL REFERENCES table_visits(id),order_id UUID NOT NULL UNIQUE REFERENCES restaurant_orders(id),
 session_id UUID NOT NULL REFERENCES dining_sessions(id),reason VARCHAR(250) NOT NULL,
 status VARCHAR(20) NOT NULL CHECK(status IN ('OPEN','VOIDED','REJECTED')),
 created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),resolved_at TIMESTAMPTZ,resolved_by UUID REFERENCES app_users(id),resolution VARCHAR(250)
);
CREATE INDEX idx_order_disputes_visit ON order_disputes(tenant_id,branch_id,visit_id,status);
