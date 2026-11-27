-- Printed QR codes identify a table; only staff-issued dining codes authorize a visit.
ALTER TABLE table_visits ADD COLUMN dining_code_hash VARCHAR(100),ADD COLUMN dining_code_expires_at TIMESTAMPTZ,
 ADD COLUMN dining_code_failures INTEGER NOT NULL DEFAULT 0 CHECK(dining_code_failures BETWEEN 0 AND 5);
CREATE TABLE dining_sessions(
 id UUID PRIMARY KEY,visit_id UUID NOT NULL REFERENCES table_visits(id),token_hash CHAR(64) NOT NULL UNIQUE,
 issued_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),expires_at TIMESTAMPTZ NOT NULL,revoked_at TIMESTAMPTZ
);
CREATE INDEX idx_dining_sessions_visit ON dining_sessions(visit_id) WHERE revoked_at IS NULL;
ALTER TABLE restaurant_orders ADD COLUMN dining_session_id UUID REFERENCES dining_sessions(id);
CREATE INDEX idx_dining_order_rate ON restaurant_orders(table_visit_id,created_at DESC);
