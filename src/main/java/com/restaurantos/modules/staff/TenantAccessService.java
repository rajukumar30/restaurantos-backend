package com.restaurantos.modules.staff;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
public class TenantAccessService {
    private final JdbcTemplate jdbc;

    public TenantAccessService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public UUID requirePermission(Authentication authentication, UUID tenantId, UUID branchId, String permission) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UUID userId)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required.");
        }
        String sql = """
                SELECT COUNT(*)
                FROM tenant_users tu
                JOIN user_roles ur ON ur.tenant_id = tu.tenant_id AND ur.tenant_user_id = tu.id
                JOIN role_permissions rp ON rp.role_id = ur.role_id
                JOIN permissions p ON p.id = rp.permission_id
                JOIN tenants t ON t.id = tu.tenant_id
                WHERE tu.tenant_id = ?
                  AND tu.user_id = ?
                  AND tu.status = 'ACTIVE'
                  AND t.status = 'ACTIVE'
                  AND p.permission_key = ?
                  AND (ur.branch_id IS NULL OR (? IS NOT NULL AND ur.branch_id = ?))
                """;
        Integer count = jdbc.queryForObject(sql, Integer.class, tenantId, userId, permission, branchId, branchId);
        if (count == null || count == 0) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have permission for this tenant or branch.");
        }
        return userId;
    }

    public boolean hasTenantWidePermission(Authentication authentication, UUID tenantId, String permission) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UUID userId)) return false;
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM tenant_users tu
                JOIN user_roles ur ON ur.tenant_id = tu.tenant_id AND ur.tenant_user_id = tu.id
                JOIN role_permissions rp ON rp.role_id = ur.role_id
                JOIN permissions p ON p.id = rp.permission_id
                JOIN tenants t ON t.id = tu.tenant_id
                WHERE tu.tenant_id = ? AND tu.user_id = ? AND tu.status = 'ACTIVE'
                  AND t.status = 'ACTIVE' AND ur.branch_id IS NULL AND p.permission_key = ?
                """, Integer.class, tenantId, userId, permission);
        return count != null && count > 0;
    }

    public void requireBranchInTenant(UUID tenantId, UUID branchId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM branches WHERE tenant_id = ? AND id = ? AND status = 'ACTIVE'",
                Integer.class, tenantId, branchId);
        if (count == null || count == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Branch not found in this restaurant.");
        }
    }
}