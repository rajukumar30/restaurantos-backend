package com.restaurantos.modules.staff.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public class TenantAccessDao {
    private final JdbcTemplate jdbc;
    public TenantAccessDao(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public boolean hasPermission(UUID userId, UUID tenantId, UUID branchId, String permission) {
        Integer count = jdbc.queryForObject("""
            SELECT COUNT(*)
            FROM tenant_users tu
            JOIN user_roles ur ON ur.tenant_id=tu.tenant_id AND ur.tenant_user_id=tu.id
            JOIN role_permissions rp ON rp.role_id=ur.role_id
            JOIN permissions p ON p.id=rp.permission_id
            JOIN tenants t ON t.id=tu.tenant_id
            WHERE tu.tenant_id=? AND tu.user_id=? AND tu.status='ACTIVE'
              AND t.status='ACTIVE' AND p.permission_key=?
              AND (ur.branch_id IS NULL OR (? IS NOT NULL AND ur.branch_id=?))
            """, Integer.class, tenantId, userId, permission, branchId, branchId);
        return count != null && count > 0;
    }
    public boolean hasTenantWidePermission(UUID userId, UUID tenantId, String permission) {
        Integer count = jdbc.queryForObject("""
            SELECT COUNT(*)
            FROM tenant_users tu
            JOIN user_roles ur ON ur.tenant_id=tu.tenant_id AND ur.tenant_user_id=tu.id
            JOIN role_permissions rp ON rp.role_id=ur.role_id
            JOIN permissions p ON p.id=rp.permission_id
            JOIN tenants t ON t.id=tu.tenant_id
            WHERE tu.tenant_id=? AND tu.user_id=? AND tu.status='ACTIVE'
              AND t.status='ACTIVE' AND ur.branch_id IS NULL AND p.permission_key=?
            """, Integer.class, tenantId, userId, permission);
        return count != null && count > 0;
    }
    public boolean activeBranchExists(UUID tenantId, UUID branchId) {
        Integer count = jdbc.queryForObject("""
            SELECT COUNT(*) FROM branches WHERE tenant_id=? AND id=? AND status='ACTIVE'
            """, Integer.class, tenantId, branchId);
        return count != null && count > 0;
    }
}
