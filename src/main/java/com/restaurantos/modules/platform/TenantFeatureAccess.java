package com.restaurantos.modules.platform;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

@Service
public class TenantFeatureAccess {
    private static final Map<String,List<String>> DEPENDENCIES = Map.of(
        "SERVICE_REQUESTS",List.of("QR_MENU"),
        "GUEST_ORDERING",List.of("QR_MENU"),
        "KITCHEN_DISPLAY",List.of("GUEST_ORDERING"),
        "WAITER_DASHBOARD",List.of("GUEST_ORDERING"),
        "ORDER_TRACKING",List.of("GUEST_ORDERING"),
        "ORDER_HISTORY",List.of("GUEST_ORDERING"),
        "DINE_IN",List.of("GUEST_ORDERING"),
        "TAKEAWAY",List.of("GUEST_ORDERING")
    );
    private final JdbcTemplate jdbc;
    public TenantFeatureAccess(JdbcTemplate jdbc) { this.jdbc=jdbc; }

    public boolean enabled(UUID tenantId, String featureKey) {
        return enabledRecursive(tenantId,featureKey,new HashSet<>());
    }

    private boolean enabledRecursive(UUID tenantId, String featureKey, Set<String> visited) {
        if (!visited.add(featureKey)) return false;
        Boolean enabled=jdbc.queryForObject("""
            SELECT CASE WHEN t.status <> 'ACTIVE' THEN FALSE
                        WHEN f.available = FALSE THEN FALSE
                        ELSE COALESCE(o.enabled, TRUE) END
            FROM tenants t CROSS JOIN feature_catalog f
            LEFT JOIN tenant_feature_overrides o
              ON o.tenant_id=t.id AND o.feature_key=f.feature_key
            WHERE t.id=? AND f.feature_key=?
            """,Boolean.class,tenantId,featureKey);
        if (!Boolean.TRUE.equals(enabled)) return false;
        for (String dependency : DEPENDENCIES.getOrDefault(featureKey,List.of())) {
            if (!enabledRecursive(tenantId,dependency,visited)) return false;
        }
        return true;
    }

    public void require(UUID tenantId, String featureKey) {
        if (!enabled(tenantId,featureKey)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "This feature is not enabled for this restaurant");
        }
    }
}
