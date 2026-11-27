ALTER TABLE table_visits ADD COLUMN floor_guest_count INTEGER CHECK(floor_guest_count BETWEEN 1 AND 50),
 ADD COLUMN floor_paid_at TIMESTAMPTZ, ADD COLUMN floor_grace_until TIMESTAMPTZ,
 ADD COLUMN floor_cleaning_started_at TIMESTAMPTZ, ADD COLUMN floor_cleaned_at TIMESTAMPTZ;
CREATE INDEX idx_floor_active_visits ON table_visits(tenant_id,branch_id,table_id) WHERE status IN ('OPEN','PAYMENT_PENDING','PAID');
CREATE INDEX idx_floor_visit_orders ON restaurant_orders(tenant_id,branch_id,table_visit_id,status);
CREATE TABLE floor_events(
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),tenant_id UUID NOT NULL REFERENCES tenants(id),branch_id UUID NOT NULL REFERENCES branches(id),
 table_id UUID NOT NULL REFERENCES restaurant_tables(id),visit_id UUID NOT NULL REFERENCES table_visits(id),
 actor_id UUID NOT NULL REFERENCES app_users(id),action VARCHAR(40) NOT NULL,created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_floor_events_visit ON floor_events(visit_id,created_at);
INSERT INTO permissions(permission_key,description) VALUES ('floor:read','View current floor state'),('floor:manage','Seat guests and manage table turnover') ON CONFLICT DO NOTHING;
INSERT INTO role_permissions(role_id,permission_id)
 SELECT r.id,p.id FROM roles r CROSS JOIN permissions p WHERE r.role_key IN ('OWNER','MANAGER','WAITER') AND p.permission_key IN ('floor:read','floor:manage') ON CONFLICT DO NOTHING;
-- Recover existing fully settled visits using the actual final recorded payment, never order creation time.
UPDATE table_visits v SET status='PAID',floor_paid_at=q.paid_at,floor_grace_until=q.paid_at+INTERVAL '5 minutes'
FROM (SELECT o.table_visit_id,MAX(p.created_at) paid_at FROM restaurant_orders o JOIN restaurant_order_payments p ON p.order_id=o.id AND p.tenant_id=o.tenant_id AND p.branch_id=o.branch_id GROUP BY o.table_visit_id) q
WHERE v.id=q.table_visit_id AND v.status='OPEN' AND NOT EXISTS (
 SELECT 1 FROM restaurant_orders o WHERE o.table_visit_id=v.id AND o.tenant_id=v.tenant_id AND o.branch_id=v.branch_id AND o.status<>'CANCELLED'
 AND (o.status<>'SERVED' OR o.total_amount>COALESCE((SELECT SUM(p.amount) FROM restaurant_order_payments p WHERE p.tenant_id=o.tenant_id AND p.branch_id=o.branch_id AND p.order_id=o.id),0)));
-- Existing confirmed visit payments receive a conservative grace period if no time is recorded.
UPDATE table_visits SET floor_paid_at=NOW(),floor_grace_until=NOW()+INTERVAL '5 minutes' WHERE status='PAID' AND floor_paid_at IS NULL;
