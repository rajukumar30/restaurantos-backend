package com.restaurantos.modules.platform;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * Server-side feature gate. Disabled by default during rollout.
 * Restaurants without an assigned subscription remain grandfathered in.
 */
@Service
public class RestaurantFeatureAccessService {
 private final JdbcTemplate jdbc;
 private final boolean enforcementEnabled;
 private final int graceDays;
 public RestaurantFeatureAccessService(JdbcTemplate jdbc,
   @Value("${restaurantos.entitlements.enforce:false}") boolean enforcementEnabled,
   @Value("${restaurantos.entitlements.grace-days:7}") int graceDays){
  this.jdbc=jdbc;this.enforcementEnabled=enforcementEnabled;this.graceDays=Math.max(0,Math.min(90,graceDays));
 }
 public void require(UUID tenantId,String featureKey){
  if(!enforcementEnabled)return;
  List<Access> access=jdbc.query("""
   SELECT s.starts_on,s.ends_on,pf.enabled AS included,f.available,
          COALESCE(o.enabled,TRUE) AS restaurant_enabled
   FROM platform_restaurant_subscriptions s
   JOIN feature_catalog f ON f.feature_key=?
   LEFT JOIN platform_plan_features pf ON pf.plan_id=s.plan_id AND pf.feature_key=f.feature_key
   LEFT JOIN tenant_feature_overrides o ON o.tenant_id=s.tenant_id AND o.feature_key=f.feature_key
   WHERE s.tenant_id=? AND s.status='ACTIVE'
   ORDER BY s.created_at DESC LIMIT 1
   """,(rs,i)->new Access(rs.getObject("starts_on",LocalDate.class),
     rs.getObject("ends_on",LocalDate.class),rs.getBoolean("included"),
     rs.getBoolean("available"),rs.getBoolean("restaurant_enabled")),featureKey,tenantId);
  if(access.isEmpty()){
   Integer previous=jdbc.queryForObject("SELECT COUNT(*) FROM platform_restaurant_subscriptions WHERE tenant_id=?",Integer.class,tenantId);
   if(previous==null||previous==0)return; // Legacy restaurants without any plan remain grandfathered.
   throw new ResponseStatusException(HttpStatus.FORBIDDEN,"No active subscription. Please contact restaurant support.");
  }
  Access a=access.get(0);
  LocalDate now=LocalDate.now(ZoneOffset.UTC);
  if(now.isBefore(a.startsOn()))
   throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Subscription has not started. Contact restaurant support.");
  if(a.endsOn()!=null&&now.isAfter(a.endsOn().plusDays(graceDays)))
   throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Subscription expired. Please renew your restaurant plan.");
  if(!a.available()||!a.included()||!a.restaurantEnabled())
   throw new ResponseStatusException(HttpStatus.FORBIDDEN,"This feature is not enabled for your restaurant plan.");
 }
 private record Access(LocalDate startsOn,LocalDate endsOn,boolean included,boolean available,boolean restaurantEnabled){}
}
