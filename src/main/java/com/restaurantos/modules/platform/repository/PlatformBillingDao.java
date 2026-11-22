package com.restaurantos.modules.platform.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class PlatformBillingDao {
    private final JdbcTemplate jdbc;
    public PlatformBillingDao(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<Map<String, Object>> recentPayments(UUID tenantId) {
        return jdbc.queryForList("""
            SELECT p.id, p.tenant_id AS "tenantId", t.name AS "restaurantName",
                   p.subscription_id AS "subscriptionId", p.amount_paise AS "amountPaise",
                   p.currency, p.status, p.provider, p.provider_payment_id AS "providerPaymentId",
                   p.created_at AS "createdAt"
            FROM platform_subscription_payments p
            JOIN tenants t ON t.id = p.tenant_id
            WHERE (?::uuid IS NULL OR p.tenant_id = ?::uuid)
            ORDER BY p.created_at DESC, p.id DESC
            LIMIT 200
            """, tenantId == null ? null : tenantId.toString(), tenantId == null ? null : tenantId.toString());
    }
}
