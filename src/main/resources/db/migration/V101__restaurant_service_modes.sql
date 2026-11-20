CREATE TABLE IF NOT EXISTS restaurant_branch_service_settings (
 tenant_id UUID NOT NULL REFERENCES tenants(id),
 branch_id UUID NOT NULL REFERENCES branches(id),
 service_mode VARCHAR(32) NOT NULL DEFAULT 'WAITER_CONFIRMED'
   CHECK (service_mode IN ('KITCHEN_MANAGED','WAITER_CONFIRMED')),
 PRIMARY KEY (tenant_id,branch_id)
);
CREATE TABLE IF NOT EXISTS restaurant_order_service_events (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
 tenant_id UUID NOT NULL REFERENCES tenants(id),
 branch_id UUID NOT NULL REFERENCES branches(id),
 order_id UUID NOT NULL REFERENCES restaurant_orders(id),
 actor_id UUID NOT NULL REFERENCES app_users(id),
 event_type VARCHAR(48) NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_restaurant_order_service_events_order ON restaurant_order_service_events(tenant_id,order_id);
