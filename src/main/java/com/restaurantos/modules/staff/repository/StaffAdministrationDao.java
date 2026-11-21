package com.restaurantos.modules.staff.repository;

import com.restaurantos.modules.staff.dto.StaffAdminDto.StaffMember;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.*;

@Repository
public class StaffAdministrationDao {
    private final JdbcTemplate jdbc;
    public StaffAdministrationDao(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public List<StaffMember> list(UUID tenantId, UUID branchId, boolean tenantWide) {
        return jdbc.query("""
            SELECT DISTINCT u.id,u.name,u.email,u.phone,u.status,
                   string_agg(DISTINCT r.display_name, ', ' ORDER BY r.display_name) AS role_names,
                   string_agg(DISTINCT b.name, ', ' ORDER BY b.name) AS branch_names
            FROM tenant_users tu
            JOIN app_users u ON u.id=tu.user_id
            LEFT JOIN user_roles ur ON ur.tenant_id=tu.tenant_id AND ur.tenant_user_id=tu.id
            LEFT JOIN roles r ON r.id=ur.role_id
            LEFT JOIN branch_users bu ON bu.tenant_id=tu.tenant_id AND bu.tenant_user_id=tu.id
              AND bu.status='ACTIVE'
            LEFT JOIN branches b ON b.id=bu.branch_id AND b.tenant_id=bu.tenant_id
            WHERE tu.tenant_id=?
              AND (? IS NULL OR EXISTS (
                SELECT 1 FROM branch_users filter_bu
                WHERE filter_bu.tenant_id=tu.tenant_id AND filter_bu.tenant_user_id=tu.id
                  AND filter_bu.branch_id=? AND filter_bu.status='ACTIVE'))
              AND (?=TRUE OR ur.branch_id IS NULL OR ur.branch_id=?)
            GROUP BY u.id,u.name,u.email,u.phone,u.status
            ORDER BY u.name
            """, (rs,i) -> new StaffMember(rs.getObject("id",UUID.class), rs.getString("name"),
                rs.getString("email"), rs.getString("phone"), rs.getString("status"),
                rs.getString("role_names"), rs.getString("branch_names")),
            tenantId, branchId, branchId, tenantWide, branchId);
    }
    public boolean emailExists(String email) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM app_users WHERE lower(email)=lower(?)",
            Integer.class, email);
        return count != null && count > 0;
    }
    public UUID findRole(UUID tenantId, String roleKey) {
        return jdbc.query("SELECT id FROM roles WHERE tenant_id=? AND role_key=?",
            (rs,i) -> rs.getObject(1,UUID.class), tenantId, roleKey).stream().findFirst().orElse(null);
    }
    public void createRole(UUID roleId, UUID tenantId, String roleKey, String name) {
        jdbc.update("INSERT INTO roles (id,tenant_id,role_key,display_name,is_system) VALUES (?,?,?,?,true)",
            roleId, tenantId, roleKey, name);
    }
    public void grantPermissions(UUID roleId, List<String> permissions) {
        jdbc.batchUpdate("""
            INSERT INTO role_permissions (role_id,permission_id)
            SELECT ?,id FROM permissions WHERE permission_key=? ON CONFLICT DO NOTHING
            """, permissions, permissions.size(), (ps, permission) -> {
                ps.setObject(1, roleId);
                ps.setString(2, permission);
            });
    }
    public void createUser(UUID userId, String name, String email, String phone, String passwordHash) {
        jdbc.update("INSERT INTO app_users (id,name,email,phone,password_hash,status) VALUES (?,?,?,?,?,'ACTIVE')",
            userId, name, email, phone, passwordHash);
    }
    public void createTenantUser(UUID tenantUserId, UUID tenantId, UUID userId) {
        jdbc.update("INSERT INTO tenant_users (id,tenant_id,user_id,status) VALUES (?,?,?,'ACTIVE')",
            tenantUserId, tenantId, userId);
    }
    public void createBranchUser(UUID branchUserId, UUID tenantId, UUID branchId, UUID tenantUserId) {
        jdbc.update("INSERT INTO branch_users (id,tenant_id,branch_id,tenant_user_id,status) VALUES (?,?,?,?,'ACTIVE')",
            branchUserId, tenantId, branchId, tenantUserId);
    }
    public void assignRole(UUID tenantId, UUID tenantUserId, UUID roleId, UUID branchId) {
        jdbc.update("INSERT INTO user_roles (tenant_id,tenant_user_id,role_id,branch_id) VALUES (?,?,?,?)",
            tenantId, tenantUserId, roleId, branchId);
    }
}
