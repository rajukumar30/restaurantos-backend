package com.restaurantos.modules.platform.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import java.util.Map;
import java.util.UUID;

@Repository
public class PlatformBillingDao {
    private final JdbcTemplate jdbc;
    public PlatformBillingDao(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public boolean subscriptionBelongsToTenant(UUID subscriptionId, UUID tenantId) {
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM platform_restaurant_subscriptions WHERE id=? AND tenant_id=?",
            Integer.class, subscriptionId, tenantId);
        return count != null && count == 1;
    }

    public Map<String, Object> createPending(UUID tenantId, UUID subscriptionId, long amountPaise,
                                               String idempotencyKey) {
        // Never accept payment success from a client. Every record starts as PENDING.
        try {
            return jdbc.queryForMap("""
                INSERT INTO platform_subscription_payments
                    (tenant_id, subscription_id, amount_paise, provider, idempotency_key)
                VALUES (?, ?, ?, 'MANUAL', ?)
                RETURNING id, tenant_id AS "tenantId", subscription_id AS "subscriptionId",
                          amount_paise AS "amountPaise", status, provider, created_at AS "createdAt"
                """, tenantId, subscriptionId, amountPaise, idempotencyKey);
        } catch (DuplicateKeyException duplicate) {
            Map<String, Object> existing = jdbc.queryForMap("""
                SELECT id, tenant_id AS "tenantId", subscription_id AS "subscriptionId",
                       amount_paise AS "amountPaise", status, provider, created_at AS "createdAt"
                FROM platform_subscription_payments WHERE idempotency_key=?
                """, idempotencyKey);
            if (!tenantId.equals(existing.get("tenantId")) || !subscriptionId.equals(existing.get("subscriptionId"))
                || ((Number) existing.get("amountPaise")).longValue() != amountPaise) {
                throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.CONFLICT, "Idempotency key already used for another payment");
            }
            return existing;
        }
    }

    public Map<String, Object> findByIdempotencyKey(String key) {
        return jdbc.query("""
            SELECT id, tenant_id AS "tenantId", subscription_id AS "subscriptionId",
                   amount_paise AS "amountPaise", currency, status, provider,
                   provider_payment_id AS "providerPaymentId", created_at AS "createdAt"
            FROM platform_subscription_payments WHERE idempotency_key=?
            """, (rs, i) -> {
                Map<String, Object> row = new java.util.LinkedHashMap<>();
                row.put("id", rs.getObject("id", UUID.class));
                row.put("tenantId", rs.getObject("tenantId", UUID.class));
                row.put("subscriptionId", rs.getObject("subscriptionId", UUID.class));
                row.put("amountPaise", rs.getLong("amountPaise"));
                row.put("currency", rs.getString("currency"));
                row.put("status", rs.getString("status"));
                row.put("provider", rs.getString("provider"));
                row.put("providerPaymentId", rs.getString("providerPaymentId"));
                row.put("createdAt", rs.getTimestamp("createdAt").toInstant());
                return row;
            }, key).stream().findFirst().orElse(null);
    }

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
