CREATE TABLE menu_categories (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    name VARCHAR(100) NOT NULL,
    description VARCHAR(300),
    sort_order INTEGER NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_menu_categories_tenant_id_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_menu_categories_tenant_name UNIQUE (tenant_id, name)
);

CREATE TABLE menu_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    category_id UUID NOT NULL,
    name VARCHAR(150) NOT NULL,
    description VARCHAR(500),
    base_price NUMERIC(10,2) NOT NULL CHECK (base_price > 0),
    dietary_type VARCHAR(20) NOT NULL DEFAULT 'VEG'
        CHECK (dietary_type IN ('VEG', 'NON_VEG', 'EGG')),
    prep_time_minutes INTEGER NOT NULL DEFAULT 15 CHECK (prep_time_minutes BETWEEN 1 AND 240),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_menu_items_category
        FOREIGN KEY (tenant_id, category_id)
        REFERENCES menu_categories(tenant_id, id),
    CONSTRAINT uq_menu_items_tenant_id_id UNIQUE (tenant_id, id)
);

-- Menu item definitions are shared at restaurant level; branch rows control
-- whether an item is offered and its branch-specific price.
CREATE TABLE branch_menu_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    branch_id UUID NOT NULL,
    menu_item_id UUID NOT NULL,
    price_override NUMERIC(10,2) CHECK (price_override IS NULL OR price_override > 0),
    is_available BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_branch_menu_items_branch
        FOREIGN KEY (tenant_id, branch_id)
        REFERENCES branches(tenant_id, id),
    CONSTRAINT fk_branch_menu_items_item
        FOREIGN KEY (tenant_id, menu_item_id)
        REFERENCES menu_items(tenant_id, id),
    CONSTRAINT uq_branch_menu_items_branch_item UNIQUE (tenant_id, branch_id, menu_item_id)
);

CREATE INDEX idx_menu_categories_tenant_active
    ON menu_categories(tenant_id, is_active, sort_order);
CREATE INDEX idx_menu_items_tenant_category
    ON menu_items(tenant_id, category_id, is_active);
CREATE INDEX idx_branch_menu_items_branch
    ON branch_menu_items(tenant_id, branch_id, is_available);
