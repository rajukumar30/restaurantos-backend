package com.restaurantos.modules.platform;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
public class TenantFeatureAccess {
    private final JdbcTemplate jdbc;
    public TenantFeatureAccess(JdbcTemplate jdbc) { this.jdbc=jdbc; }

    public boolean enabled(UUID tenantId, String featureKey) {
        Boolean enabled=jdbc.queryForObject("""
            SELECT CASE WHEN t.status <> 'ACTIVE' THEN FALSE
                        WHEN f.available = FALSE THEN FALSE
                        ELSE COALESCE(o.enabled, TRUE) END
            FROM tenants t CROSS JOIN feature_catalog f
            LEFT JOIN tenant_feature_overrides o
              ON o.tenant_id=t.id AND o.feature_key=f.feature_key
            WHERE t.id=? AND f.feature_key=?
            """,Boolean.class,tenantId,featureKey);
        return Boolean.TRUE.equals(enabled);
    }

    public void require(UUID tenantId, String featureKey) {
        if (!enabled(tenantId,featureKey)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "This feature is not enabled for this restaurant");
        }
    }
}
