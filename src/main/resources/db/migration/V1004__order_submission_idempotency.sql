-- Prevent duplicate QR orders when a guest retries the same submission.
ALTER TABLE restaurant_orders
  ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(100),
  ADD COLUMN IF NOT EXISTS request_hash VARCHAR(64);
CREATE UNIQUE INDEX IF NOT EXISTS ux_restaurant_order_submission
  ON restaurant_orders (tenant_id, table_id, idempotency_key)
  WHERE idempotency_key IS NOT NULL;
