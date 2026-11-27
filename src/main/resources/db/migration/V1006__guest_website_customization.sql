-- Published settings are a single atomic tenant configuration. Preserve legacy branding.
ALTER TABLE restaurant_guest_branding ADD COLUMN settings_json JSONB;
