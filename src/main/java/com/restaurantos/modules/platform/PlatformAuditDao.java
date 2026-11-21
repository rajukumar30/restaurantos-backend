package com.restaurantos.modules.platform;
import com.restaurantos.common.api.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;


import com.restaurantos.modules.platform.dto.PlatformAuditDto.*;
import org.springframework.stereotype.Repository;
@Repository
public class PlatformAuditDao {
 private final JdbcTemplate jdbc;
 
 public PlatformAuditDao(JdbcTemplate jdbc){this.jdbc=jdbc;}
         public List<AuditEvent> list(int page,
                                              UUID tenantId) {

        if (page < 0 || page > 100000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid page");
        }
        List<AuditEvent> events = jdbc.query("""
            SELECT a.id,a.actor_user_id,u.name AS actor_name,a.tenant_id,
                   t.name AS restaurant_name,a.action,a.feature_key,
                   a.old_value,a.new_value,a.created_at
            FROM platform_admin_audit_logs a
            JOIN app_users u ON u.id=a.actor_user_id
            JOIN tenants t ON t.id=a.tenant_id
            WHERE (CAST(? AS UUID) IS NULL OR a.tenant_id=CAST(? AS UUID))
            ORDER BY a.created_at DESC,a.id DESC
            LIMIT 25 OFFSET ?
            """, (rs, index) -> new AuditEvent(
                rs.getObject("id",UUID.class),
                rs.getObject("actor_user_id",UUID.class),
                rs.getString("actor_name"),
                rs.getObject("tenant_id",UUID.class),
                rs.getString("restaurant_name"),
                rs.getString("action"),
                rs.getString("feature_key"),
                (Boolean)rs.getObject("old_value"),
                (Boolean)rs.getObject("new_value"),
                rs.getObject("created_at",OffsetDateTime.class)),
            tenantId,tenantId,page*25);
        return events;
 }
}
