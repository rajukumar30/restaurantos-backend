CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE tenants (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(150) NOT NULL,
    slug VARCHAR(100) NOT NULL UNIQUE,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'SUSPENDED', 'INACTIVE')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE branches (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    name VARCHAR(150) NOT NULL,
    branch_code VARCHAR(40) NOT NULL,
    address JSONB NOT NULL DEFAULT '{}'::jsonb,
    timezone VARCHAR(60) NOT NULL DEFAULT 'Asia/Kolkata',
    phone VARCHAR(20),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'INACTIVE')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_branches_tenant_id_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_branches_tenant_code UNIQUE (tenant_id, branch_code)
);

CREATE TABLE app_users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(150) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    phone VARCHAR(20),
    password_hash VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'INVITED', 'LOCKED', 'INACTIVE')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE tenant_users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    user_id UUID NOT NULL REFERENCES app_users(id),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'INVITED', 'INACTIVE')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_tenant_users_tenant_user UNIQUE (tenant_id, user_id),
    CONSTRAINT uq_tenant_users_tenant_id_id UNIQUE (tenant_id, id)
);

CREATE TABLE branch_users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    branch_id UUID NOT NULL,
    tenant_user_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'INACTIVE')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_branch_users_branch
        FOREIGN KEY (tenant_id, branch_id)
        REFERENCES branches(tenant_id, id),
    CONSTRAINT fk_branch_users_tenant_user
        FOREIGN KEY (tenant_id, tenant_user_id)
        REFERENCES tenant_users(tenant_id, id),
    CONSTRAINT uq_branch_users_membership
        UNIQUE (tenant_id, branch_id, tenant_user_id)
);

CREATE TABLE roles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID REFERENCES tenants(id),
    role_key VARCHAR(60) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    is_system BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_roles_tenant_id_id UNIQUE (tenant_id, id)
);

CREATE UNIQUE INDEX uq_roles_global_key
    ON roles(role_key) WHERE tenant_id IS NULL;

CREATE UNIQUE INDEX uq_roles_tenant_key
    ON roles(tenant_id, role_key) WHERE tenant_id IS NOT NULL;

CREATE TABLE permissions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    permission_key VARCHAR(100) NOT NULL UNIQUE,
    description VARCHAR(255)
);

CREATE TABLE role_permissions (
    role_id UUID NOT NULL REFERENCES roles(id),
    permission_id UUID NOT NULL REFERENCES permissions(id),
    PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE user_roles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    tenant_user_id UUID NOT NULL,
    role_id UUID NOT NULL,
    branch_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_user_roles_tenant_user
        FOREIGN KEY (tenant_id, tenant_user_id)
        REFERENCES tenant_users(tenant_id, id),
    CONSTRAINT fk_user_roles_role
        FOREIGN KEY (tenant_id, role_id)
        REFERENCES roles(tenant_id, id),
    CONSTRAINT fk_user_roles_branch
        FOREIGN KEY (tenant_id, branch_id)
        REFERENCES branches(tenant_id, id)
);

CREATE INDEX idx_branches_tenant_status ON branches(tenant_id, status);
CREATE INDEX idx_tenant_users_user ON tenant_users(user_id, status);
CREATE INDEX idx_branch_users_branch ON branch_users(tenant_id, branch_id, status);
CREATE INDEX idx_user_roles_membership ON user_roles(tenant_id, tenant_user_id);
