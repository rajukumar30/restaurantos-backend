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
              AND (CAST(? AS UUID) IS NULL OR EXISTS (
                SELECT 1 FROM branch_users filter_bu
                WHERE filter_bu.tenant_id=tu.tenant_id AND filter_bu.tenant_user_id=tu.id
                  AND filter_bu.branch_id=? AND filter_bu.status='ACTIVE'))
              AND (CAST(? AS BOOLEAN)=TRUE OR ur.branch_id IS NULL OR ur.branch_id=?)
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
    /** Only single-tenant, single-branch operational accounts are editable through this workflow. */
    public Optional<UUID> lockEditableMembership(UUID tenant,UUID branch,UUID user){
        return jdbc.query("""
            SELECT tu.id FROM tenant_users tu JOIN app_users u ON u.id=tu.user_id
            JOIN branch_users bu ON bu.tenant_id=tu.tenant_id AND bu.tenant_user_id=tu.id
            WHERE tu.tenant_id=? AND bu.branch_id=? AND u.id=? AND bu.status='ACTIVE'
              AND u.status IN ('ACTIVE','INACTIVE') AND tu.status IN ('ACTIVE','INACTIVE')
              AND NOT EXISTS (SELECT 1 FROM platform_admins pa WHERE pa.user_id=u.id)
              AND (SELECT COUNT(*) FROM tenant_users other WHERE other.user_id=u.id)=1
              AND (SELECT COUNT(*) FROM branch_users other WHERE other.tenant_id=tu.tenant_id AND other.tenant_user_id=tu.id)=1
              AND (SELECT COUNT(*) FROM user_roles ur WHERE ur.tenant_id=tu.tenant_id AND ur.tenant_user_id=tu.id)=1
              AND EXISTS (SELECT 1 FROM user_roles ur JOIN roles r ON r.id=ur.role_id AND r.tenant_id=ur.tenant_id
                 WHERE ur.tenant_id=tu.tenant_id AND ur.tenant_user_id=tu.id AND ur.branch_id=bu.branch_id
                   AND r.role_key IN ('MANAGER','KITCHEN','CASHIER','WAITER','STAFF'))
            FOR UPDATE OF tu,u
            """,(rs,row)->rs.getObject(1,UUID.class),tenant,branch,user).stream().findFirst();
    }
    public void updateStaff(UUID tenant,UUID branch,UUID membership,UUID user,String name,String phone,String status,UUID role){
        jdbc.update("UPDATE app_users SET name=?,phone=?,status=?,updated_at=NOW() WHERE id=?",name,phone,status,user);
        // Inactive membership blocks tenant permissions even for already-issued JWTs.
        jdbc.update("UPDATE tenant_users SET status=?,updated_at=NOW() WHERE tenant_id=? AND id=? AND user_id=?",status,tenant,membership,user);
        jdbc.update("UPDATE user_roles SET role_id=? WHERE tenant_id=? AND tenant_user_id=? AND branch_id=?",role,tenant,membership,branch);
    }
}
