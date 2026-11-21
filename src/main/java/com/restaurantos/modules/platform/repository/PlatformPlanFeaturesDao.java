package com.restaurantos.modules.platform.repository;
import com.restaurantos.modules.platform.dto.PlatformPlanFeatureDto.PlanFeature;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.*;
@Repository
public class PlatformPlanFeaturesDao {
 private final JdbcTemplate jdbc;
 private static final Map<String,List<String>> DEPENDENCIES=Map.of(
  "GUEST_ORDERING",List.of("QR_MENU"),"KITCHEN_DISPLAY",List.of("GUEST_ORDERING"),
  "WAITER_DASHBOARD",List.of("GUEST_ORDERING"),"ORDER_TRACKING",List.of("GUEST_ORDERING"),
  "ORDER_HISTORY",List.of("GUEST_ORDERING"),"SERVICE_REQUESTS",List.of("QR_MENU"),
  "DINE_IN",List.of("GUEST_ORDERING"),"TAKEAWAY",List.of("GUEST_ORDERING"));
 public PlatformPlanFeaturesDao(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public List<String> status(UUID id,boolean lock){
  return jdbc.query(lock?"SELECT status FROM platform_subscription_plans WHERE id=? FOR UPDATE":
   "SELECT status FROM platform_subscription_plans WHERE id=?",(rs,i)->rs.getString(1),id);
 }
 public List<PlanFeature> load(UUID id){
  return jdbc.query("""
   SELECT f.feature_key,f.display_name,f.category,f.available,pf.enabled AS included
   FROM feature_catalog f LEFT JOIN platform_plan_features pf
    ON pf.feature_key=f.feature_key AND pf.plan_id=?
   ORDER BY f.category,f.display_name
   """,(rs,i)->new PlanFeature(rs.getString("feature_key"),rs.getString("display_name"),
   rs.getString("category"),rs.getBoolean("available"),rs.getBoolean("included"),
   DEPENDENCIES.getOrDefault(rs.getString("feature_key"),List.of())),id);
 }
 public boolean hasSubscriptions(UUID id){return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM platform_restaurant_subscriptions WHERE plan_id=?)",Boolean.class,id));}
 public void set(UUID id,String key,boolean included){
  jdbc.update("""
   INSERT INTO platform_plan_features(plan_id,feature_key,enabled)
   VALUES(?,?,?) ON CONFLICT(plan_id,feature_key) DO UPDATE
   SET enabled=EXCLUDED.enabled,updated_at=NOW()
   """,id,key,included);
 }
}
