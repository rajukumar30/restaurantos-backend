-- Keep legacy image rows readable; all newly written images must meet new limits.
ALTER TABLE menu_item_images ADD COLUMN thumbnail_bytes BYTEA;
ALTER TABLE menu_item_images ADD CONSTRAINT ck_menu_detail_size CHECK (octet_length(image_bytes) BETWEEN 1 AND 102400) NOT VALID;
ALTER TABLE menu_item_images ADD CONSTRAINT ck_menu_thumbnail_size CHECK (thumbnail_bytes IS NOT NULL AND octet_length(thumbnail_bytes) BETWEEN 1 AND 40960) NOT VALID;
ALTER TABLE restaurant_brand_images ADD CONSTRAINT ck_brand_fast_image_size CHECK (
 (slot='LOGO' AND octet_length(image_bytes)<=40960) OR
 (slot='BANNER' AND octet_length(image_bytes)<=122880)
) NOT VALID;
