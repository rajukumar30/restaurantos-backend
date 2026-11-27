-- Subscription plan catalog. Prices are configured by platform administrators; no assumed pricing.
CREATE TABLE platform_subscription_plans (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
 name VARCHAR(100) NOT NULL UNIQUE,
 description VARCHAR(500),
 monthly_price_paise BIGINT NOT NULL CHECK (monthly_price_paise >= 0),
 annual_price_paise BIGINT NOT NULL CHECK (annual_price_paise >= 0),
 status VARCHAR(16) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT','ACTIVE','ARCHIVED')),
 created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
