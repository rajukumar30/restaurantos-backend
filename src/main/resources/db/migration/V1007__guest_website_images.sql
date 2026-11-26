CREATE TABLE restaurant_brand_images (
 id UUID PRIMARY KEY,
 tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
 slot VARCHAR(10) NOT NULL CHECK (slot IN ('LOGO','BANNER')),
 image_bytes BYTEA NOT NULL CHECK (octet_length(image_bytes) BETWEEN 1 AND 512000),
 UNIQUE(tenant_id,slot)
);
