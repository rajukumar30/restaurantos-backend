-- Date-range access and bounded live queue; keep the existing status index.
CREATE INDEX IF NOT EXISTS idx_orders_branch_date_id ON restaurant_orders(tenant_id,branch_id,created_at DESC,id DESC);
CREATE INDEX IF NOT EXISTS idx_orders_branch_open_date_id ON restaurant_orders(tenant_id,branch_id,created_at DESC,id DESC) WHERE status IN ('NEW','ACCEPTED','PREPARING','READY');
