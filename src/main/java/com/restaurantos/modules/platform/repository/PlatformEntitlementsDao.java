package com.restaurantos.modules.platform.repository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.*;
@Repository
public class PlatformEntitlementsDao {
 private final JdbcTemplate jdbc;
 public PlatformEntitlementsDao(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public boolean tenantExists(UUID tenantId){
  Integer count=jdbc.queryForObject("SELECT COUNT(*) FROM tenants WHERE id=?",Integer.class,tenantId);
  return count!=null&&count>0;
 }
 public List<Map<String,Object>> subscriptions(UUID tenantId){
  return jdbc.queryForList("""
   SELECT s.plan_id,p.name AS plan_name,s.starts_on,s.ends_on,s.status
   FROM platform_restaurant_subscriptions s JOIN platform_subscription_plans p ON p.id=s.plan_id
   WHERE s.tenant_id=? AND s.status='ACTIVE' ORDER BY s.created_at DESC LIMIT 1
   """,tenantId);
 }
 public List<FeatureFlags> features(UUID tenantId,UUID planId){
  return jdbc.query("""
   SELECT f.feature_key,f.display_name,f.category,f.available,
    COALESCE(pf.enabled,FALSE) AS plan_included,
    CASE WHEN o.expires_at IS NULL OR o.expires_at > NOW() THEN COALESCE(o.enabled,TRUE) ELSE TRUE END AS restaurant_enabled
   FROM feature_catalog f
   LEFT JOIN platform_plan_features pf ON pf.feature_key=f.feature_key AND pf.plan_id=?
   LEFT JOIN tenant_feature_overrides o ON o.feature_key=f.feature_key AND o.tenant_id=?
   ORDER BY f.category,f.display_name
   """,(rs,i)->new FeatureFlags(rs.getString("feature_key"),rs.getString("display_name"),
    rs.getString("category"),rs.getBoolean("available"),rs.getBoolean("plan_included"),
    rs.getBoolean("restaurant_enabled"),rs.getBoolean("complimentary_grant")),planId,tenantId,tenantId);
 }
 public record FeatureFlags(String key,String name,String category,boolean available,boolean included,boolean enabled,boolean grantActive){}
}
