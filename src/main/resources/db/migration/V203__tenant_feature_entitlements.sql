-- Additive restaurant-level feature entitlements. Existing restaurants retain current behavior
-- until feature checks are integrated into individual business endpoints.
CREATE TABLE feature_catalog (
 feature_key VARCHAR(80) PRIMARY KEY,
 display_name VARCHAR(120) NOT NULL,
 category VARCHAR(60) NOT NULL,
 description VARCHAR(500),
 available BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE TABLE tenant_feature_overrides (
 tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
 feature_key VARCHAR(80) NOT NULL REFERENCES feature_catalog(feature_key),
 enabled BOOLEAN NOT NULL,
 updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 updated_by UUID REFERENCES app_users(id),
 PRIMARY KEY (tenant_id, feature_key)
);
INSERT INTO feature_catalog(feature_key,display_name,category,description,available) VALUES
 ('QR_MENU','QR digital menu','Guest','QR-accessible restaurant menu',TRUE),
 ('GUEST_ORDERING','Guest ordering','Guest','Place orders from a table QR code',TRUE),
 ('KITCHEN_DISPLAY','Kitchen display','Operations','Kitchen order preparation dashboard',TRUE),
 ('WAITER_DASHBOARD','Waiter dashboard','Operations','Waiter order and service actions',TRUE),
 ('SERVICE_REQUESTS','Service requests','Guest','Call waiter, water, cleaning and bill requests',TRUE),
 ('ORDER_TRACKING','Order tracking','Guest','Guest-facing live order progress',TRUE),
 ('ORDER_HISTORY','Order history','Guest','Verified guest order history',TRUE),
 ('DIGITAL_RECEIPTS','Digital receipts','Billing','Downloadable bill receipts',TRUE),
 ('OWNER_ANALYTICS','Owner analytics','Management','Restaurant reporting',TRUE),
 ('STAFF_MANAGEMENT','Staff management','Management','Restaurant staff access',TRUE),
 ('MULTI_BRANCH','Multiple branches','Management','Multiple restaurant branches',TRUE),
 ('DINE_IN','Dine-in ordering','Guest','Dine-in table orders',TRUE),
 ('TAKEAWAY','Takeaway ordering','Guest','Takeaway order flow',TRUE),
 ('ONLINE_PAYMENTS','Online payments','Billing','Provider-backed payment processing',FALSE),
 ('WHATSAPP_RECEIPTS','WhatsApp receipts','Billing','Approved messaging provider integration',FALSE);
