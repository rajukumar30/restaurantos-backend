package com.restaurantos.modules.platform.repository;

import com.restaurantos.modules.platform.dto.PlatformSubscriptionDto.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Repository
public class PlatformSubscriptionDao {
    private final JdbcTemplate jdbc;
    public PlatformSubscriptionDao(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    private static final String SELECT = """
        SELECT s.id, s.tenant_id, t.name AS restaurant_name, s.plan_id, p.name AS plan_name,
               s.billing_cycle, s.starts_on, s.free_until, s.ends_on, s.status
        FROM platform_restaurant_subscriptions s
        JOIN tenants t ON t.id = s.tenant_id
        JOIN platform_subscription_plans p ON p.id = s.plan_id
        """;

    private Subscription map(ResultSet rs, int ignored) throws SQLException {
        LocalDate start = rs.getObject("starts_on", LocalDate.class);
        LocalDate free = rs.getObject("free_until", LocalDate.class);
        LocalDate end = rs.getObject("ends_on", LocalDate.class);
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        String status = rs.getString("status");
        boolean withinTerm = !today.isBefore(start) && (end == null || !today.isAfter(end));
        boolean freeNow = "ACTIVE".equals(status) && withinTerm && free != null && !today.isAfter(free);
        String lifecycle = "CANCELLED".equals(status) ? "CANCELLED"
            : today.isBefore(start) ? "SCHEDULED"
            : end != null && today.isAfter(end) ? "EXPIRED"
            : freeNow ? "FREE_PERIOD" : "ACTIVE";
        return new Subscription(rs.getObject("id", UUID.class), rs.getObject("tenant_id", UUID.class),
            rs.getString("restaurant_name"), rs.getObject("plan_id", UUID.class),
            rs.getString("plan_name"), rs.getString("billing_cycle"), start, free, end,
            status, freeNow, lifecycle);
    }

    public List<Subscription> list(UUID tenantId) {
        String id = tenantId == null ? null : tenantId.toString();
        return jdbc.query(SELECT + """
            WHERE (?::uuid IS NULL OR s.tenant_id = ?::uuid)
            ORDER BY s.created_at DESC LIMIT 200
            """, this::map, id, id);
    }
    public Subscription find(UUID id) {
        return jdbc.queryForObject(SELECT + " WHERE s.id = ?", this::map, id);
    }
    public boolean isActivePlan(UUID id) {
        return jdbc.query("SELECT status FROM platform_subscription_plans WHERE id = ?",
            (rs, i) -> rs.getString(1), id).stream().anyMatch("ACTIVE"::equals);
    }
    public boolean tenantExists(UUID tenantId) {
        return !jdbc.query("SELECT id FROM tenants WHERE id = ?",
            (rs, i) -> rs.getObject(1, UUID.class), tenantId).isEmpty();
    }
    public void lockTenant(UUID tenantId) {
        jdbc.queryForObject("SELECT id FROM tenants WHERE id = ? FOR UPDATE", UUID.class, tenantId);
    }
    public void cancelActiveForTenant(UUID tenantId) {
        jdbc.update("UPDATE platform_restaurant_subscriptions SET status='CANCELLED',updated_at=NOW() WHERE tenant_id=? AND status='ACTIVE'", tenantId);
    }
    public UUID insert(Assign req) {
        return jdbc.queryForObject("""
            INSERT INTO platform_restaurant_subscriptions(tenant_id,plan_id,billing_cycle,starts_on,free_until,ends_on)
            VALUES(?,?,?,?,?,?) RETURNING id
            """, UUID.class, req.tenantId(), req.planId(), req.billingCycle(), req.startsOn(), req.freeUntil(), req.endsOn());
    }
    public UUID tenantForSubscription(UUID id) {
        return jdbc.query("SELECT tenant_id FROM platform_restaurant_subscriptions WHERE id = ?",
            (rs, i) -> rs.getObject(1, UUID.class), id).stream().findFirst().orElse(null);
    }
    public int cancelActive(UUID id) {
        return jdbc.update("""
            UPDATE platform_restaurant_subscriptions SET status='CANCELLED',updated_at=NOW()
            WHERE id=? AND status='ACTIVE'
            """, id);
    }
    public void audit(UUID actor, UUID tenantId, String action) {
        jdbc.update("INSERT INTO platform_admin_audit_logs(actor_user_id,tenant_id,action) VALUES(?,?,?)",
            actor, tenantId, action);
    }
}
