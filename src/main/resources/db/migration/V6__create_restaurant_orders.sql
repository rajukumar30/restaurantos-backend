CREATE TABLE restaurant_orders (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    branch_id UUID NOT NULL,
    table_id UUID NOT NULL,
    order_number VARCHAR(24) NOT NULL,
    order_source VARCHAR(20) NOT NULL DEFAULT 'QR'
        CHECK (order_source IN ('QR', 'STAFF', 'TAKEAWAY')),
    status VARCHAR(20) NOT NULL DEFAULT 'NEW'
        CHECK (status IN ('NEW', 'ACCEPTED', 'PREPARING', 'READY', 'SERVED', 'CANCELLED')),
    customer_name VARCHAR(120),
    notes VARCHAR(500),
    total_amount NUMERIC(12,2) NOT NULL CHECK (total_amount >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_restaurant_orders_branch
        FOREIGN KEY (tenant_id, branch_id) REFERENCES branches(tenant_id, id),
    CONSTRAINT fk_restaurant_orders_table
        FOREIGN KEY (tenant_id, table_id) REFERENCES restaurant_tables(tenant_id, id),
    CONSTRAINT uq_restaurant_orders_tenant_id_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_restaurant_orders_branch_number UNIQUE (tenant_id, branch_id, order_number)
);

CREATE TABLE restaurant_order_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    order_id UUID NOT NULL,
    menu_item_id UUID NOT NULL,
    item_name VARCHAR(150) NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity BETWEEN 1 AND 20),
    unit_price NUMERIC(10,2) NOT NULL CHECK (unit_price > 0),
    line_total NUMERIC(12,2) NOT NULL CHECK (line_total > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_order_items_order
        FOREIGN KEY (tenant_id, order_id) REFERENCES restaurant_orders(tenant_id, id),
    CONSTRAINT fk_order_items_menu_item
        FOREIGN KEY (tenant_id, menu_item_id) REFERENCES menu_items(tenant_id, id)
);

CREATE INDEX idx_restaurant_orders_branch_status_created
    ON restaurant_orders(tenant_id, branch_id, status, created_at DESC);
CREATE INDEX idx_restaurant_order_items_order
    ON restaurant_order_items(tenant_id, order_id);

INSERT INTO permissions(permission_key, description) VALUES
    ('orders:read', 'View branch orders'),
    ('orders:manage', 'Update order status and operations')
ON CONFLICT (permission_key) DO NOTHING;

INSERT INTO role_permissions(role_id, permission_id)
SELECT rp.role_id, p_new.id
FROM role_permissions rp
JOIN permissions p_old ON p_old.id = rp.permission_id AND p_old.permission_key = 'menu:read'
JOIN permissions p_new ON p_new.permission_key = 'orders:read'
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions(role_id, permission_id)
SELECT rp.role_id, p_new.id
FROM role_permissions rp
JOIN permissions p_old ON p_old.id = rp.permission_id AND p_old.permission_key = 'menu:manage'
JOIN permissions p_new ON p_new.permission_key = 'orders:manage'
ON CONFLICT DO NOTHING;
