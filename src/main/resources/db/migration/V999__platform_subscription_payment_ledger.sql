-- Platform subscription payment ledger. Separate from guest restaurant order payments.
CREATE TABLE IF NOT EXISTS platform_subscription_payments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    subscription_id UUID NOT NULL REFERENCES platform_restaurant_subscriptions(id),
    amount_paise BIGINT NOT NULL CHECK (amount_paise > 0),
    currency VARCHAR(3) NOT NULL DEFAULT 'INR' CHECK (currency = 'INR'),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','SUCCEEDED','FAILED','REFUNDED')),
    provider VARCHAR(40) NOT NULL,
    provider_payment_id VARCHAR(200),
    idempotency_key VARCHAR(150) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_platform_subscription_payment_idempotency UNIQUE (idempotency_key),
    CONSTRAINT uq_platform_subscription_provider_payment UNIQUE (provider, provider_payment_id)
);
CREATE INDEX IF NOT EXISTS idx_platform_subscription_payments_tenant_created
    ON platform_subscription_payments (tenant_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_platform_subscription_payments_subscription
    ON platform_subscription_payments (subscription_id);
