package com.restaurantos.modules.platform.repository;

import com.restaurantos.modules.platform.dto.PlatformRestaurantDto.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public class PlatformRestaurantDirectoryDao {
    private final JdbcTemplate jdbc;
    public PlatformRestaurantDirectoryDao(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    private Restaurant restaurant(ResultSet rs, int ignored) throws SQLException {
        return new Restaurant(rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("slug"),
            rs.getString("subdomain"), rs.getString("status"), rs.getObject("created_at", OffsetDateTime.class));
    }
    private BranchDetail branch(ResultSet rs, int ignored) throws SQLException {
        return new BranchDetail(rs.getObject("id", UUID.class), rs.getString("name"),
            rs.getString("branch_code"), rs.getString("phone"), rs.getString("status"),
            rs.getString("timezone"), rs.getString("address_text"));
    }
    public List<Restaurant> list(String term, int page) {
        return jdbc.query("""
            SELECT id,name,slug,subdomain,status,created_at FROM tenants
            WHERE (? = '' OR name ILIKE '%' || ? || '%' OR slug ILIKE '%' || ? || '%')
            ORDER BY created_at DESC,id DESC LIMIT 25 OFFSET ?
            """, this::restaurant, term, term, term, page * 25);
    }
    public Restaurant find(UUID tenantId) {
        return jdbc.query("""
            SELECT id,name,slug,subdomain,status,created_at FROM tenants WHERE id=?
            """, this::restaurant, tenantId).stream().findFirst().orElse(null);
    }
    public List<BranchDetail> branches(UUID tenantId) {
        return jdbc.query("""
            SELECT id,name,branch_code,phone,status,timezone,address::text AS address_text
            FROM branches WHERE tenant_id=? ORDER BY created_at,id
            """, this::branch, tenantId);
    }
    public List<OwnerDetail> owners(UUID tenantId) {
        return jdbc.query("""
            SELECT DISTINCT u.id,u.name,u.email,u.phone,u.status
            FROM app_users u
            JOIN tenant_users tu ON tu.user_id=u.id AND tu.tenant_id=?
            JOIN user_roles ur ON ur.tenant_user_id=tu.id AND ur.tenant_id=tu.tenant_id
            JOIN roles r ON r.id=ur.role_id
            WHERE UPPER(r.role_key) IN ('OWNER','RESTAURANT_OWNER')
            ORDER BY u.name,u.id
            """, (rs, i) -> new OwnerDetail(rs.getObject("id", UUID.class), rs.getString("name"),
                rs.getString("email"), rs.getString("phone"), rs.getString("status")), tenantId);
    }
    public void updateRestaurant(UUID tenantId, String name, String status) {
        jdbc.update("UPDATE tenants SET name=?,status=?,updated_at=NOW() WHERE id=?", name, status, tenantId);
    }
    public BranchDetail branchForUpdate(UUID tenantId, UUID branchId) {
        return jdbc.query("""
            SELECT id,name,branch_code,phone,status,timezone,address::text AS address_text
            FROM branches WHERE tenant_id=? AND id=? FOR UPDATE
            """, this::branch, tenantId, branchId).stream().findFirst().orElse(null);
    }
    public void updateBranch(UUID tenantId, UUID branchId, String name, String phone, String status) {
        jdbc.update("""
            UPDATE branches SET name=?,phone=?,status=?,updated_at=NOW()
            WHERE tenant_id=? AND id=?
            """, name, phone, status, tenantId, branchId);
    }
    public void audit(UUID actor, UUID tenantId, String action) {
        jdbc.update("INSERT INTO platform_admin_audit_logs(actor_user_id,tenant_id,action) VALUES(?,?,?)",
            actor, tenantId, action);
    }
}
