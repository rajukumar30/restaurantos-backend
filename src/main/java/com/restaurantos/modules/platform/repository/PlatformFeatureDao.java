package com.restaurantos.modules.platform.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.time.OffsetDateTime;
import java.util.UUID;

@Repository
public class PlatformFeatureDao {
    private final JdbcTemplate jdbc;
    public PlatformFeatureDao(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public record FeatureRow(String key, String name, String category, String description,
                             boolean available, Boolean overrideEnabled, OffsetDateTime expiresAt) {}

    public boolean tenantExists(UUID tenantId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM tenants WHERE id=?", Integer.class, tenantId);
        return count != null && count > 0;
    }
    public void lockTenant(UUID tenantId) {
        jdbc.queryForObject("SELECT id FROM tenants WHERE id=? FOR UPDATE", UUID.class, tenantId);
    }
    public List<FeatureRow> load(UUID tenantId) {
        return jdbc.query("""
            SELECT f.feature_key,f.display_name,f.category,f.description,f.available,
                   CASE WHEN o.expires_at IS NULL OR o.expires_at > NOW() THEN o.enabled ELSE NULL END AS override_enabled,
                   CASE WHEN o.expires_at > NOW() THEN o.expires_at ELSE NULL END AS expires_at
            FROM feature_catalog f
            LEFT JOIN tenant_feature_overrides o
              ON o.feature_key=f.feature_key AND o.tenant_id=CAST(? AS UUID)
            ORDER BY f.category,f.display_name
            """, (rs,i) -> new FeatureRow(rs.getString("feature_key"), rs.getString("display_name"),
                rs.getString("category"), rs.getString("description"), rs.getBoolean("available"),
                (Boolean) rs.getObject("override_enabled"), rs.getObject("expires_at", OffsetDateTime.class)), tenantId);
    }
    public void audit(UUID actor, UUID tenantId, String featureKey, boolean oldValue, boolean newValue) {
        jdbc.update("""
            INSERT INTO platform_admin_audit_logs
                (actor_user_id,tenant_id,action,feature_key,old_value,new_value)
            VALUES(?,?,'FEATURE_OVERRIDE_UPDATED',?,?,?)
            """, actor, tenantId, featureKey, oldValue, newValue);
    }
    public void upsertOverride(UUID tenantId, String featureKey, boolean enabled, UUID actor, OffsetDateTime expiresAt) {
        jdbc.update("""
            INSERT INTO tenant_feature_overrides(tenant_id,feature_key,enabled,updated_by,expires_at)
            VALUES(?,?,?,?,?)
            ON CONFLICT(tenant_id,feature_key) DO UPDATE SET
              enabled=EXCLUDED.enabled,updated_by=EXCLUDED.updated_by,updated_at=NOW(),expires_at=EXCLUDED.expires_at
            """, tenantId, featureKey, enabled, actor, expiresAt);
    }
}
