-- Separate recipient-level email outbox: durable retries and audit status.
CREATE TABLE platform_invoice_reminder_emails (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
 reminder_id UUID NOT NULL REFERENCES platform_invoice_reminders(id),
 recipient_email VARCHAR(320) NOT NULL,
 status VARCHAR(16) NOT NULL DEFAULT 'PENDING'
   CHECK (status IN ('PENDING','SENDING','SENT','FAILED')),
 attempts INT NOT NULL DEFAULT 0 CHECK(attempts >= 0),
 next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 lease_until TIMESTAMPTZ,
 sent_at TIMESTAMPTZ,
 last_error VARCHAR(500),
 created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 CONSTRAINT uq_reminder_email_recipient UNIQUE(reminder_id,recipient_email)
);
CREATE INDEX idx_reminder_emails_dispatch
 ON platform_invoice_reminder_emails(status,next_attempt_at);
