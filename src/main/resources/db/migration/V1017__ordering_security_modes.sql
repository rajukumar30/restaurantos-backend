-- Explicit, branch-scoped opt-in. Existing restaurants keep Secure mode.
CREATE TABLE branch_order_security(
 tenant_id UUID NOT NULL REFERENCES tenants(id),branch_id UUID NOT NULL REFERENCES branches(id),
 mode VARCHAR(10) NOT NULL CHECK(mode IN ('SECURE','EXPRESS','STRICT')),
 max_order_amount NUMERIC(12,2) NOT NULL CHECK(max_order_amount BETWEEN 100 AND 5000),
 max_visit_amount NUMERIC(12,2) NOT NULL CHECK(max_visit_amount BETWEEN max_order_amount AND 10000),
 max_units INTEGER NOT NULL CHECK(max_units BETWEEN 1 AND 20),
 updated_by UUID NOT NULL REFERENCES app_users(id),updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 PRIMARY KEY(tenant_id,branch_id)
);
ALTER TABLE dining_sessions ADD COLUMN verified BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE restaurant_orders ADD COLUMN express_unverified BOOLEAN NOT NULL DEFAULT FALSE;
CREATE TABLE order_review_requests(
 id UUID PRIMARY KEY,tenant_id UUID NOT NULL REFERENCES tenants(id),branch_id UUID NOT NULL REFERENCES branches(id),
 table_id UUID NOT NULL REFERENCES restaurant_tables(id),visit_id UUID NOT NULL REFERENCES table_visits(id),
 session_id UUID NOT NULL REFERENCES dining_sessions(id),order_number VARCHAR(40) NOT NULL,
 request_key VARCHAR(100) NOT NULL,request_hash CHAR(64) NOT NULL,request_json JSONB NOT NULL,
 total_amount NUMERIC(12,2) NOT NULL,items_summary VARCHAR(4000) NOT NULL,reason VARCHAR(100) NOT NULL,
 status VARCHAR(10) NOT NULL DEFAULT 'PENDING' CHECK(status IN ('PENDING','APPROVED','REJECTED')),
 created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),expires_at TIMESTAMPTZ NOT NULL,
 resolved_at TIMESTAMPTZ,resolved_by UUID REFERENCES app_users(id),resolution VARCHAR(250),
 UNIQUE(visit_id,request_key)
);
CREATE INDEX idx_review_branch ON order_review_requests(tenant_id,branch_id,created_at) WHERE status='PENDING';
CREATE INDEX idx_review_visit ON order_review_requests(visit_id,created_at DESC);
CREATE INDEX idx_express_visit ON restaurant_orders(table_visit_id) WHERE express_unverified;
