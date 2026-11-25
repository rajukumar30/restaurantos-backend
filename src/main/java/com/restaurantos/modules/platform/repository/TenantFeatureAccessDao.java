package com.restaurantos.modules.platform.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public class TenantFeatureAccessDao {
    private final JdbcTemplate jdbc;
    public TenantFeatureAccessDao(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public boolean allEntitled(UUID tenantId, java.util.Set<String> keys) {
        if (!validKeys(tenantId, keys)) return false;
        Long count = jdbc.queryForObject(entitlementSql(keys), Long.class, arguments(tenantId, keys));
        return count != null && count == keys.size();
    }

    public java.util.Set<String> entitledKeys(UUID tenantId, java.util.Set<String> keys) {
        if (!validKeys(tenantId, keys)) return java.util.Set.of();
        String sql = entitlementSql(keys).replace("SELECT count(DISTINCT f.feature_key)", "SELECT DISTINCT f.feature_key");
        return java.util.Set.copyOf(jdbc.queryForList(sql, String.class, arguments(tenantId, keys)));
    }

    private boolean validKeys(UUID tenantId, java.util.Set<String> keys) {
        return tenantId != null && keys != null && !keys.isEmpty() && keys.stream().allMatch(k -> k != null && k.matches("[A-Z][A-Z0-9_]{0,63}"));
    }

    private String entitlementSql(java.util.Set<String> keys) {
        String placeholders = String.join(",", java.util.Collections.nCopies(keys.size(), "?"));
        return """
            SELECT count(DISTINCT f.feature_key)
            FROM tenants t
            JOIN LATERAL (
                SELECT plan_id, starts_on, ends_on
                FROM platform_restaurant_subscriptions
                WHERE tenant_id=t.id AND status='ACTIVE'
                ORDER BY created_at DESC LIMIT 1
            ) s ON TRUE
            CROSS JOIN feature_catalog f
            LEFT JOIN platform_plan_features pf ON pf.plan_id=s.plan_id AND pf.feature_key=f.feature_key
            LEFT JOIN tenant_feature_overrides o ON o.tenant_id=t.id AND o.feature_key=f.feature_key
            WHERE t.id=? AND t.status='ACTIVE' AND f.available=TRUE
              AND s.starts_on <= (NOW() AT TIME ZONE 'UTC')::date
              AND (s.ends_on IS NULL OR s.ends_on >= (NOW() AT TIME ZONE 'UTC')::date)
              AND CASE WHEN o.expires_at IS NULL OR o.expires_at > NOW() THEN COALESCE(o.enabled,TRUE) ELSE TRUE END
              AND (pf.enabled=TRUE OR EXISTS (
                  SELECT 1 FROM tenant_feature_grants g WHERE g.tenant_id=t.id
                  AND g.feature_key=f.feature_key AND g.revoked_at IS NULL
                  AND g.starts_at<=NOW() AND g.expires_at>NOW()
              ))
              AND f.feature_key IN (
            """ + placeholders + ")";
    }

    private Object[] arguments(UUID tenantId, java.util.Set<String> keys) {
        java.util.List<Object> args = new java.util.ArrayList<>();
        args.add(tenantId);
        args.addAll(keys);
        return args.toArray();
    }

    public boolean enabled(UUID tenantId, String featureKey) {
        if (tenantId == null || featureKey == null || !featureKey.matches("[A-Z][A-Z0-9_]{0,63}")) return false;
        try {
        Boolean enabled = jdbc.queryForObject("""
            SELECT CASE WHEN t.status <> 'ACTIVE' THEN FALSE
                        WHEN f.available = FALSE THEN FALSE
                        ELSE CASE WHEN o.expires_at IS NULL OR o.expires_at > NOW() THEN COALESCE(o.enabled,TRUE) ELSE TRUE END END
            FROM tenants t CROSS JOIN feature_catalog f
            LEFT JOIN tenant_feature_overrides o
              ON o.tenant_id=t.id AND o.feature_key=f.feature_key
            WHERE t.id=? AND f.feature_key=?
            """, Boolean.class, tenantId, featureKey);
        return Boolean.TRUE.equals(enabled);
        } catch (EmptyResultDataAccessException missing) {
            return false;
        }
    }
}
