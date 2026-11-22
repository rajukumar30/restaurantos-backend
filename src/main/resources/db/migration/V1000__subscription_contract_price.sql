-- A negotiated subscription price is attached to the restaurant's subscription,
-- not to a manually entered payment attempt. NULL uses the plan catalog price.
ALTER TABLE platform_restaurant_subscriptions
  ADD COLUMN custom_price_paise BIGINT NULL
  CONSTRAINT chk_subscription_custom_price_nonnegative CHECK (custom_price_paise >= 0 AND custom_price_paise <= 100000000000);
