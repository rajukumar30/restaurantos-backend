package com.restaurantos.modules.staff;

import com.restaurantos.common.api.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/branches")
public class TenantBranchController {
    private final JdbcTemplate jdbc;
    private final TenantAccessService access;

    public TenantBranchController(JdbcTemplate jdbc, TenantAccessService access) {
        this.jdbc = jdbc;
        this.access = access;
    }

    @GetMapping
    public ApiResponse<List<BranchSummary>> list(@PathVariable UUID tenantId, Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UUID userId)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required.");
        }
        boolean tenantWide = access.hasTenantWidePermission(authentication, tenantId, "branch:read");
        if (!tenantWide) {
            Integer allowed = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM tenant_users tu
                    JOIN user_roles ur ON ur.tenant_id = tu.tenant_id AND ur.tenant_user_id = tu.id
                    JOIN role_permissions rp ON rp.role_id = ur.role_id
                    JOIN permissions p ON p.id = rp.permission_id
                    JOIN branches b ON b.id = ur.branch_id AND b.tenant_id = ur.tenant_id
                    WHERE tu.tenant_id = ? AND tu.user_id = ? AND tu.status = 'ACTIVE'
                      AND b.status = 'ACTIVE' AND p.permission_key = 'branch:read'
                    """, Integer.class, tenantId, userId);
            if (allowed == null || allowed == 0) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have permission to view branches.");
            }
        }
        List<BranchSummary> branches = jdbc.query("""
                SELECT b.id, b.name, b.branch_code, b.address::text AS address, b.phone
                FROM branches b
                WHERE b.tenant_id = ? AND b.status = 'ACTIVE'
                  AND (? = TRUE OR EXISTS (
                    SELECT 1 FROM tenant_users tu
                    JOIN user_roles ur ON ur.tenant_id = tu.tenant_id AND ur.tenant_user_id = tu.id
                    WHERE tu.tenant_id = b.tenant_id AND tu.user_id = ?
                      AND tu.status = 'ACTIVE' AND ur.branch_id = b.id
                  ))
                ORDER BY b.name
                """, (rs, row) -> new BranchSummary(rs.getObject("id", UUID.class), rs.getString("name"),
                rs.getString("branch_code"), rs.getString("address"), rs.getString("phone")),
                tenantId, tenantWide, userId);
        return ApiResponse.success("Branches loaded", branches);
    }

    public record BranchSummary(UUID id, String name, String branchCode, String address, String phone) {}
}