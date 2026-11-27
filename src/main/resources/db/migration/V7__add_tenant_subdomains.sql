-- Give every restaurant tenant a unique DNS-safe subdomain.
ALTER TABLE tenants ADD COLUMN subdomain VARCHAR(63);

-- Backfill existing tenants before enforcing NOT NULL.
UPDATE tenants
SET subdomain = CASE
    WHEN length(slug) <= 63
         AND slug ~ '^[a-z0-9]([a-z0-9-]*[a-z0-9])?$'
         AND slug NOT IN ('www','admin','api','app','support','login','mail','status','docs')
      THEN slug
    ELSE 'restaurant-' || substr(replace(id::text, '-', ''), 1, 8)
END;

ALTER TABLE tenants ALTER COLUMN subdomain SET NOT NULL;
ALTER TABLE tenants
    ADD CONSTRAINT uq_tenants_subdomain UNIQUE (subdomain);
ALTER TABLE tenants
    ADD CONSTRAINT chk_tenants_subdomain_format
    CHECK (subdomain ~ '^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?$');
