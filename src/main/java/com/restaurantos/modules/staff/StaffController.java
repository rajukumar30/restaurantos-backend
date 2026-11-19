package com.restaurantos.modules.staff;

import com.restaurantos.common.api.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/staff")
public class StaffController {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final TenantAccessService access;

    public StaffController(JdbcTemplate jdbc, PasswordEncoder encoder, TenantAccessService access) {
        this.jdbc = jdbc;
        this.encoder = encoder;
        this.access = access;
    }

    @GetMapping
    public ApiResponse<List<StaffMember>> list(@PathVariable UUID tenantId,
                                                @RequestParam(required = false) UUID branchId,
                                                Authentication authentication) {
        access.requirePermission(authentication, tenantId, branchId, "staff:read");
        String sql = """
                SELECT DISTINCT u.id, u.name, u.email, u.phone, u.status,
                       string_agg(DISTINCT r.display_name, ', ' ORDER BY r.display_name) AS role_names,
                       string_agg(DISTINCT b.name, ', ' ORDER BY b.name) AS branch_names
                FROM tenant_users tu
                JOIN app_users u ON u.id = tu.user_id
                LEFT JOIN user_roles ur ON ur.tenant_id = tu.tenant_id AND ur.tenant_user_id = tu.id
                LEFT JOIN roles r ON r.id = ur.role_id
                LEFT JOIN branch_users bu ON bu.tenant_id = tu.tenant_id
                     AND bu.tenant_user_id = tu.id AND bu.status = 'ACTIVE'
                LEFT JOIN branches b ON b.id = bu.branch_id AND b.tenant_id = bu.tenant_id
                WHERE tu.tenant_id = ?
                  AND (? IS NULL OR EXISTS (
                      SELECT 1 FROM branch_users filter_bu
                      WHERE filter_bu.tenant_id = tu.tenant_id
                        AND filter_bu.tenant_user_id = tu.id
                        AND filter_bu.branch_id = ? AND filter_bu.status = 'ACTIVE'
                  ))
                GROUP BY u.id, u.name, u.email, u.phone, u.status
                ORDER BY u.name
                """;
        List<StaffMember> staff = jdbc.query(sql, (rs, row) -> new StaffMember(
                rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("email"),
                rs.getString("phone"), rs.getString("status"), rs.getString("role_names"),
                rs.getString("branch_names")), tenantId, branchId, branchId);
        return ApiResponse.success("Staff loaded", staff);
    }

    @PostMapping
    @Transactional
    public ApiResponse<StaffCreated> invite(@PathVariable UUID tenantId,
                                             @Valid @RequestBody InviteStaffRequest request,
                                             Authentication authentication) {
        access.requirePermission(authentication, tenantId, request.branchId(), "staff:manage");
        access.requireBranchInTenant(tenantId, request.branchId());

        String roleKey = request.roleKey().trim().toUpperCase(Locale.ROOT);
        if (!List.of("MANAGER", "KITCHEN", "CASHIER", "WAITER", "STAFF").contains(roleKey)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported role. Choose MANAGER, KITCHEN, CASHIER, WAITER or STAFF.");
        }
        Integer existing = jdbc.queryForObject("SELECT COUNT(*) FROM app_users WHERE lower(email)=lower(?)",
                Integer.class, request.email());
        if (existing != null && existing > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This email already has an account. Multi-tenant staff invitations are not enabled yet.");
        }

        UUID userId = UUID.randomUUID();
        UUID tenantUserId = UUID.randomUUID();
        UUID branchUserId = UUID.randomUUID();
        UUID roleId = ensureRole(tenantId, roleKey);

        jdbc.update("INSERT INTO app_users (id,name,email,phone,password_hash,status) VALUES (?,?,?,?,?,'ACTIVE')",
                userId, request.name().trim(), request.email().toLowerCase(Locale.ROOT),
                request.phone(), encoder.encode(request.temporaryPassword()));
        jdbc.update("INSERT INTO tenant_users (id,tenant_id,user_id,status) VALUES (?,?,?,'ACTIVE')",
                tenantUserId, tenantId, userId);
        jdbc.update("INSERT INTO branch_users (id,tenant_id,branch_id,tenant_user_id,status) VALUES (?,?,?,?,'ACTIVE')",
                branchUserId, tenantId, request.branchId(), tenantUserId);
        jdbc.update("INSERT INTO user_roles (tenant_id,tenant_user_id,role_id,branch_id) VALUES (?,?,?,?)",
                tenantId, tenantUserId, roleId, request.branchId());

        return ApiResponse.success("Staff account created",
                new StaffCreated(userId, tenantUserId, request.branchId(), roleKey, "ACTIVE"));
    }

    private UUID ensureRole(UUID tenantId, String roleKey) {
        List<UUID> found = jdbc.query("SELECT id FROM roles WHERE tenant_id = ? AND role_key = ?",
                (rs, row) -> rs.getObject("id", UUID.class), tenantId, roleKey);
        if (!found.isEmpty()) return found.getFirst();

        UUID roleId = UUID.randomUUID();
        String displayName = switch (roleKey) {
            case "MANAGER" -> "Branch Manager";
            case "KITCHEN" -> "Kitchen Staff";
            case "CASHIER" -> "Cashier";
            case "WAITER" -> "Waiter";
            default -> "Staff";
        };
        jdbc.update("INSERT INTO roles (id,tenant_id,role_key,display_name,is_system) VALUES (?,?,?,?,true)",
                roleId, tenantId, roleKey, displayName);

        List<String> permissions = switch (roleKey) {
            case "MANAGER" -> List.of("staff:read", "branch:read", "menu:read", "menu:manage", "orders:read",
                    "orders:manage", "kitchen:operate", "payments:read", "reports:read");
            case "KITCHEN" -> List.of("orders:read", "kitchen:operate");
            case "CASHIER" -> List.of("orders:read", "payments:read");
            case "WAITER" -> List.of("orders:read", "orders:manage");
            default -> List.of("orders:read");
        };
        jdbc.batchUpdate("INSERT INTO role_permissions (role_id,permission_id) SELECT ?,id FROM permissions WHERE permission_key=? ON CONFLICT DO NOTHING",
                permissions, permissions.size(), (ps, permission) -> {
                    ps.setObject(1, roleId);
                    ps.setString(2, permission);
                });
        return roleId;
    }

    public record InviteStaffRequest(@NotBlank @Size(max=150) String name,
                                     @NotBlank @Email @Size(max=255) String email,
                                     @Size(max=20) String phone,
                                     @NotBlank @Size(min=12,max=100) String temporaryPassword,
                                     @NotNull UUID branchId,
                                     @NotBlank String roleKey) {}
    public record StaffMember(UUID userId, String name, String email, String phone,
                              String accountStatus, String roles, String branches) {}
    public record StaffCreated(UUID userId, UUID tenantUserId, UUID branchId, String roleKey, String status) {}
}