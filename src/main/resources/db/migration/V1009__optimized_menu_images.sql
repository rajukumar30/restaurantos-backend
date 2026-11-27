CREATE TABLE menu_item_images (
 id UUID PRIMARY KEY,
 tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
 item_id UUID NOT NULL UNIQUE REFERENCES menu_items(id) ON DELETE CASCADE,
 image_bytes BYTEA NOT NULL CHECK(octet_length(image_bytes) BETWEEN 1 AND 184320)
);
CREATE INDEX idx_menu_item_images_tenant ON menu_item_images(tenant_id);
