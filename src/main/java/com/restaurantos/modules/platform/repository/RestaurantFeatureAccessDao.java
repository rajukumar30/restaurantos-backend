package com.restaurantos.modules.platform.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.*;

@Repository
public class RestaurantFeatureAccessDao {
    private final JdbcTemplate jdbc;
    public RestaurantFeatureAccessDao(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public record Access(LocalDate startsOn, LocalDate endsOn, boolean included,
                         boolean available, boolean restaurantEnabled) {}

    public Access latestActive(UUID tenantId, String featureKey) {
        return jdbc.query("""
            SELECT s.starts_on,s.ends_on,pf.enabled AS included,f.available,
                   COALESCE(o.enabled,TRUE) AS restaurant_enabled
            FROM platform_restaurant_subscriptions s
            JOIN feature_catalog f ON f.feature_key=?
            LEFT JOIN platform_plan_features pf
              ON pf.plan_id=s.plan_id AND pf.feature_key=f.feature_key
            LEFT JOIN tenant_feature_overrides o
              ON o.tenant_id=s.tenant_id AND o.feature_key=f.feature_key
            WHERE s.tenant_id=? AND s.status='ACTIVE'
            ORDER BY s.created_at DESC LIMIT 1
            """, (rs,i) -> new Access(rs.getObject("starts_on",LocalDate.class),
                rs.getObject("ends_on",LocalDate.class),rs.getBoolean("included"),
                rs.getBoolean("available"),rs.getBoolean("restaurant_enabled")),
            featureKey, tenantId).stream().findFirst().orElse(null);
    }
    public boolean hasAnySubscription(UUID tenantId) {
        Integer count = jdbc.queryForObject("""
            SELECT COUNT(*) FROM platform_restaurant_subscriptions WHERE tenant_id=?
            """, Integer.class, tenantId);
        return count != null && count > 0;
    }
}
