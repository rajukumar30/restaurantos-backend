-- Keep already-published legacy photos readable; all new/replaced photos obey tighter slot limits.
ALTER TABLE restaurant_brand_images ADD CONSTRAINT ck_brand_image_optimized_size CHECK (
 (slot='LOGO' AND octet_length(image_bytes)<=122880) OR
 (slot='BANNER' AND octet_length(image_bytes)<=256000)
) NOT VALID;
