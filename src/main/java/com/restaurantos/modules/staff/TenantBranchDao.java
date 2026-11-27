package com.restaurantos.modules.staff;

import com.restaurantos.modules.staff.dto.TenantBranchDto.BranchSummary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.*;

@Repository
public class TenantBranchDao {
    private final JdbcTemplate jdbc;
    public TenantBranchDao(JdbcTemplate jdbc) { this.jdbc=jdbc; }

    public boolean hasBranchReadPermission(UUID tenantId, UUID userId) {
        Integer allowed=jdbc.queryForObject("""
            SELECT COUNT(*) FROM tenant_users tu
            JOIN user_roles ur ON ur.tenant_id=tu.tenant_id AND ur.tenant_user_id=tu.id
            JOIN role_permissions rp ON rp.role_id=ur.role_id
            JOIN permissions p ON p.id=rp.permission_id
            JOIN branches b ON b.id=ur.branch_id AND b.tenant_id=ur.tenant_id
            WHERE tu.tenant_id=? AND tu.user_id=? AND tu.status='ACTIVE'
              AND b.status='ACTIVE' AND p.permission_key='branch:read'
            """, Integer.class, tenantId, userId);
        return allowed!=null && allowed>0;
    }

    public List<BranchSummary> list(UUID tenantId, UUID userId, boolean tenantWide) {
        return jdbc.query("""
            SELECT b.id,b.name,b.branch_code,b.address::text AS address,b.phone
            FROM branches b
            WHERE b.tenant_id=? AND b.status='ACTIVE'
              AND (?=TRUE OR EXISTS (
                SELECT 1 FROM tenant_users tu
                JOIN user_roles ur ON ur.tenant_id=tu.tenant_id AND ur.tenant_user_id=tu.id
                WHERE tu.tenant_id=b.tenant_id AND tu.user_id=?
                  AND tu.status='ACTIVE' AND ur.branch_id=b.id
              ))
            ORDER BY b.name
            """,(rs,i)->new BranchSummary(rs.getObject("id",UUID.class),rs.getString("name"),
                rs.getString("branch_code"),rs.getString("address"),rs.getString("phone")),
            tenantId,tenantWide,userId);
    }
}
