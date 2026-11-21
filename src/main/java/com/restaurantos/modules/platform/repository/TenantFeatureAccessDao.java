package com.restaurantos.modules.platform.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public class TenantFeatureAccessDao {
    private final JdbcTemplate jdbc;
    public TenantFeatureAccessDao(JdbcTemplate jdbc) { this.jdbc = jdbc; }

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
