CREATE TABLE platform_restaurant_subscriptions (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
 tenant_id UUID NOT NULL REFERENCES tenants(id),
 plan_id UUID NOT NULL REFERENCES platform_subscription_plans(id),
 billing_cycle VARCHAR(10) NOT NULL CHECK (billing_cycle IN ('MONTHLY','ANNUAL')),
 starts_on DATE NOT NULL,
 free_until DATE,
 ends_on DATE,
 status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','CANCELLED')),
 created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 CHECK (free_until IS NULL OR free_until >= starts_on),
 CHECK (ends_on IS NULL OR ends_on >= starts_on)
);
CREATE UNIQUE INDEX idx_one_active_restaurant_subscription ON platform_restaurant_subscriptions(tenant_id) WHERE status='ACTIVE';
CREATE INDEX idx_platform_restaurant_subscriptions_tenant ON platform_restaurant_subscriptions(tenant_id,created_at DESC);
