CREATE TABLE IF NOT EXISTS restaurant_customer_demo_otp_challenges (
 phone_e164 VARCHAR(20) PRIMARY KEY,
 expires_at TIMESTAMPTZ NOT NULL,
 attempts INT NOT NULL DEFAULT 0,
 last_requested_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
