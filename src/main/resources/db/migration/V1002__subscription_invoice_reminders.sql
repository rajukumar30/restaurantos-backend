-- Persistent, deduplicated reminder events. No email is sent by this migration.
CREATE TABLE platform_invoice_reminders (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
 invoice_id UUID NOT NULL REFERENCES platform_subscription_invoices(id),
 tenant_id UUID NOT NULL REFERENCES tenants(id),
 reminder_type VARCHAR(20) NOT NULL CHECK (reminder_type IN ('DUE_7_DAYS','DUE_3_DAYS','DUE_TODAY','OVERDUE_1_DAY','OVERDUE_3_DAYS')),
 scheduled_on DATE NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 CONSTRAINT uq_invoice_reminder_type UNIQUE(invoice_id,reminder_type)
);
CREATE INDEX idx_invoice_reminders_tenant ON platform_invoice_reminders(tenant_id,created_at DESC);
