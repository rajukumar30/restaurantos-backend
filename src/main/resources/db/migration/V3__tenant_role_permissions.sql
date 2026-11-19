-- Seed baseline permissions for system roles as well as tenant roles.
INSERT INTO permissions (permission_key, description) VALUES
 ('staff:read', 'View staff members in an assigned tenant or branch'),
 ('staff:manage', 'Invite staff and assign tenant roles'),
 ('branch:read', 'View branches in the tenant'),
 ('branch:manage', 'Manage tenant branches'),
 ('menu:read', 'View menu items'),
 ('menu:manage', 'Manage menu items'),
 ('orders:read', 'View orders'),
 ('orders:manage', 'Manage order workflow'),
 ('kitchen:operate', 'Operate kitchen order queue'),
 ('payments:read', 'View payments and settlements'),
 ('reports:read', 'View sales and operational reports')
ON CONFLICT (permission_key) DO NOTHING;

-- A tenant owner can manage every branch, including branches created later.
UPDATE user_roles
SET branch_id = NULL
WHERE role_id IN (SELECT id FROM roles WHERE role_key = 'OWNER' AND tenant_id IS NOT NULL);

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.role_key = 'OWNER' AND r.tenant_id IS NOT NULL
ON CONFLICT DO NOTHING;

CREATE INDEX idx_user_roles_tenant_role ON user_roles(tenant_id, role_id);
CREATE INDEX idx_role_permissions_permission ON role_permissions(permission_id);
