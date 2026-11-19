CREATE TABLE restaurant_tables (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    branch_id UUID NOT NULL,
    table_number VARCHAR(30) NOT NULL,
    display_name VARCHAR(80) NOT NULL,
    seating_capacity INTEGER NOT NULL DEFAULT 2 CHECK (seating_capacity BETWEEN 1 AND 50),
    qr_token VARCHAR(64) NOT NULL UNIQUE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_restaurant_tables_branch
        FOREIGN KEY (tenant_id, branch_id) REFERENCES branches(tenant_id, id),
    CONSTRAINT uq_restaurant_tables_branch_number UNIQUE (tenant_id, branch_id, table_number),
    CONSTRAINT uq_restaurant_tables_tenant_id_id UNIQUE (tenant_id, id)
);

CREATE INDEX idx_restaurant_tables_branch_active
    ON restaurant_tables(tenant_id, branch_id, is_active);

INSERT INTO permissions(permission_key, description) VALUES
    ('tables:read', 'View restaurant tables and QR codes'),
    ('tables:manage', 'Create and update restaurant tables and QR codes')
ON CONFLICT (permission_key) DO NOTHING;

INSERT INTO role_permissions(role_id, permission_id)
SELECT rp.role_id, p_new.id
FROM role_permissions rp
JOIN permissions p_old ON p_old.id = rp.permission_id AND p_old.permission_key = 'menu:read'
JOIN permissions p_new ON p_new.permission_key = 'tables:read'
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions(role_id, permission_id)
SELECT rp.role_id, p_new.id
FROM role_permissions rp
JOIN permissions p_old ON p_old.id = rp.permission_id AND p_old.permission_key = 'menu:manage'
JOIN permissions p_new ON p_new.permission_key = 'tables:manage'
ON CONFLICT DO NOTHING;
