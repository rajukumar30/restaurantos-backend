-- Invoice ledger: billing obligations, not proof of payment.
CREATE TABLE platform_subscription_invoices (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
 tenant_id UUID NOT NULL REFERENCES tenants(id),
 subscription_id UUID NOT NULL REFERENCES platform_restaurant_subscriptions(id),
 period_start DATE NOT NULL,
 period_end DATE NOT NULL,
 due_on DATE NOT NULL,
 amount_paise BIGINT NOT NULL CHECK (amount_paise >= 0),
 currency VARCHAR(3) NOT NULL DEFAULT 'INR' CHECK (currency = 'INR'),
 status VARCHAR(16) NOT NULL DEFAULT 'UNPAID' CHECK (status IN ('UNPAID','PAID','VOID')),
 created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 CONSTRAINT chk_invoice_period CHECK (period_end >= period_start),
 CONSTRAINT uq_subscription_invoice_period UNIQUE(subscription_id,period_start,period_end)
);
CREATE INDEX idx_subscription_invoices_tenant_due ON platform_subscription_invoices(tenant_id,due_on DESC);
