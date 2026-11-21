package com.restaurantos.modules.platform.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public class RestaurantOnboardingDao {
    private final JdbcTemplate jdbc;
    public RestaurantOnboardingDao(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public boolean slugExists(String slug) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM tenants WHERE slug = ?", Integer.class, slug);
        return count != null && count > 0;
    }
    public boolean subdomainExists(String name) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM tenants WHERE subdomain = ?", Integer.class, name);
        return count != null && count > 0;
    }
    public boolean emailExists(String email) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM app_users WHERE lower(email)=lower(?)", Integer.class, email);
        return count != null && count > 0;
    }
    public void insertTenant(UUID tenantId, String name, String slug, String subdomain) {
        jdbc.update("INSERT INTO tenants (id,name,slug,subdomain) VALUES (?,?,?,?)", tenantId, name, slug, subdomain);
    }
    public void insertBranch(UUID branchId, UUID tenantId, String name, String address, String phone) {
        jdbc.update("INSERT INTO branches (id,tenant_id,name,branch_code,address,phone) VALUES (?,?,?,'MAIN',CAST(? AS jsonb),?)",
            branchId, tenantId, name, address, phone);
    }
    public void insertOwner(UUID userId, String name, String email, String phone, String passwordHash) {
        jdbc.update("INSERT INTO app_users (id,name,email,phone,password_hash,status) VALUES (?,?,?,?,?,'ACTIVE')",
            userId, name, email, phone, passwordHash);
    }
    public void insertTenantMember(UUID tenantUserId, UUID tenantId, UUID userId) {
        jdbc.update("INSERT INTO tenant_users (id,tenant_id,user_id,status) VALUES (?,?,?,'ACTIVE')",
            tenantUserId, tenantId, userId);
    }
    public void insertBranchMember(UUID branchUserId, UUID tenantId, UUID branchId, UUID tenantUserId) {
        jdbc.update("INSERT INTO branch_users (id,tenant_id,branch_id,tenant_user_id,status) VALUES (?,?,?,?,'ACTIVE')",
            branchUserId, tenantId, branchId, tenantUserId);
    }
    public void insertOwnerRole(UUID roleId, UUID tenantId) {
        jdbc.update("INSERT INTO roles (id,tenant_id,role_key,display_name,is_system) VALUES (?,?,'OWNER','Restaurant Owner',true)",
            roleId, tenantId);
    }
    public void grantAllPermissions(UUID roleId) {
        jdbc.update("""
            INSERT INTO role_permissions (role_id, permission_id)
            SELECT ?, id FROM permissions ON CONFLICT DO NOTHING
            """, roleId);
    }
    public void assignOwnerRole(UUID tenantId, UUID tenantUserId, UUID roleId) {
        jdbc.update("INSERT INTO user_roles (tenant_id,tenant_user_id,role_id,branch_id) VALUES (?,?,?,NULL)",
            tenantId, tenantUserId, roleId);
    }
}
