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

    public boolean subscriptionBelongsToTenant(UUID subscriptionId, UUID tenantId) {
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM platform_restaurant_subscriptions WHERE id=? AND tenant_id=?",
            Integer.class, subscriptionId, tenantId);
        return count != null && count == 1;
    }

    public Map<String, Object> createPending(UUID tenantId, UUID subscriptionId, long amountPaise,
                                               String idempotencyKey) {
        // Never accept payment success from a client. Every record starts as PENDING.
        // PostgreSQL ON CONFLICT is atomic under concurrent requests. Catching a unique
        // constraint exception inside an open transaction leaves PostgreSQL aborted.
        List<Map<String, Object>> inserted = jdbc.query("""
            INSERT INTO platform_subscription_payments
                (tenant_id, subscription_id, amount_paise, provider, idempotency_key)
            VALUES (?, ?, ?, 'MANUAL', ?)
            ON CONFLICT (idempotency_key) DO NOTHING
            RETURNING id, tenant_id AS "tenantId", subscription_id AS "subscriptionId",
                      amount_paise AS "amountPaise", status, provider, created_at AS "createdAt"
            """, (rs, i) -> {
                Map<String, Object> row = new java.util.LinkedHashMap<>();
                row.put("id", rs.getObject("id", UUID.class));
                row.put("tenantId", rs.getObject("tenantId", UUID.class));
                row.put("subscriptionId", rs.getObject("subscriptionId", UUID.class));
                row.put("amountPaise", rs.getLong("amountPaise"));
                row.put("status", rs.getString("status"));
                row.put("provider", rs.getString("provider"));
                row.put("createdAt", rs.getTimestamp("createdAt").toInstant());
                return row;
            }, tenantId, subscriptionId, amountPaise, idempotencyKey);
        if (!inserted.isEmpty()) return inserted.get(0);
        Map<String, Object> existing = findByIdempotencyKey(idempotencyKey);
        if (existing == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.CONFLICT, "Payment request is being processed; retry with the same key");
        }
        if (!tenantId.equals(existing.get("tenantId")) || !subscriptionId.equals(existing.get("subscriptionId"))
            || ((Number) existing.get("amountPaise")).longValue() != amountPaise) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.CONFLICT, "Idempotency key already used for another payment");
        }
        return existing;
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

    public Map<String, Object> markFailed(UUID paymentId, UUID actorId) {
        // Only a pending, manually recorded attempt can be closed. No paid state is exposed.
        List<Map<String, Object>> updated = jdbc.query("""
            UPDATE platform_subscription_payments
               SET status='FAILED', updated_at=NOW()
             WHERE id=? AND status='PENDING' AND provider='MANUAL'
            RETURNING id, tenant_id AS "tenantId", subscription_id AS "subscriptionId",
                      amount_paise AS "amountPaise", status
            """, (rs, i) -> {
                Map<String, Object> row = new java.util.LinkedHashMap<>();
                row.put("id", rs.getObject("id", UUID.class));
                row.put("tenantId", rs.getObject("tenantId", UUID.class));
                row.put("subscriptionId", rs.getObject("subscriptionId", UUID.class));
                row.put("amountPaise", rs.getLong("amountPaise"));
                row.put("status", rs.getString("status"));
                return row;
            }, paymentId);
        if (updated.isEmpty()) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.CONFLICT, "Only pending manual payments can be closed");
        }
        Map<String, Object> payment = updated.get(0);
        jdbc.update("""
            INSERT INTO platform_admin_audit_logs(actor_user_id, tenant_id, action)
            VALUES (?, ?, 'SUBSCRIPTION_PAYMENT_MARKED_FAILED')
            """, actorId, payment.get("tenantId"));
        return payment;
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
