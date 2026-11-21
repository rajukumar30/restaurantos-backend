package com.restaurantos.modules.platform;

import com.restaurantos.common.api.ApiResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;

@RestController
@RequestMapping("/api/v1/platform/restaurants")
public class PlatformEntitlementsController {
 private final JdbcTemplate jdbc;
 public PlatformEntitlementsController(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public record Entitlement(String key,String name,String category,boolean platformAvailable,
   boolean planIncluded,boolean restaurantEnabled,boolean effectiveEnabled,String reason){}
 public record Snapshot(UUID tenantId,UUID planId,String planName,String subscriptionStatus,
   boolean accessEligible,List<Entitlement> features){}
 @GetMapping("/{tenantId}/entitlements")
 public ApiResponse<Snapshot> snapshot(@PathVariable UUID tenantId){
  Integer exists=jdbc.queryForObject("SELECT COUNT(*) FROM tenants WHERE id=?",Integer.class,tenantId);
  if(exists==null||exists==0)throw new org.springframework.web.server.ResponseStatusException(
   org.springframework.http.HttpStatus.NOT_FOUND,"Restaurant not found");
  LocalDate now=LocalDate.now(ZoneOffset.UTC);
  List<Map<String,Object>> subscriptions=jdbc.queryForList("""
   SELECT s.plan_id,p.name AS plan_name,s.starts_on,s.ends_on,s.status
   FROM platform_restaurant_subscriptions s JOIN platform_subscription_plans p ON p.id=s.plan_id
   WHERE s.tenant_id=? AND s.status='ACTIVE' ORDER BY s.created_at DESC LIMIT 1
   """,tenantId);
  UUID planId=null;String planName=null;String state="UNASSIGNED";boolean eligible=false;
  if(!subscriptions.isEmpty()){
   Map<String,Object> s=subscriptions.get(0);
   planId=(UUID)s.get("plan_id");planName=(String)s.get("plan_name");
   LocalDate start=((java.sql.Date)s.get("starts_on")).toLocalDate();
   Object endValue=s.get("ends_on");
   LocalDate end=endValue==null?null:((java.sql.Date)endValue).toLocalDate();
   state=now.isBefore(start)?"SCHEDULED":end!=null&&now.isAfter(end)?"EXPIRED":"ACTIVE";
   eligible="ACTIVE".equals(state);
  }
  final UUID selectedPlan=planId;final boolean active=eligible;final boolean assigned=planId!=null;
  List<Entitlement> features=jdbc.query("""
   SELECT f.feature_key,f.display_name,f.category,f.available,
     COALESCE(pf.enabled,FALSE) AS plan_included,
     COALESCE(o.enabled,TRUE) AS restaurant_enabled
   FROM feature_catalog f
   LEFT JOIN platform_plan_features pf ON pf.feature_key=f.feature_key AND pf.plan_id=?
   LEFT JOIN tenant_feature_overrides o ON o.feature_key=f.feature_key AND o.tenant_id=?
   ORDER BY f.category,f.display_name
   """,(rs,i)->{
    boolean available=rs.getBoolean("available");
    boolean included=rs.getBoolean("plan_included");
    boolean restaurantEnabled=rs.getBoolean("restaurant_enabled");
    boolean effective=active&&available&&included&&restaurantEnabled;
    String reason=!assigned?"NO_SUBSCRIPTION":!active?"SUBSCRIPTION_INACTIVE":!available?"PLATFORM_UNAVAILABLE":!included?"NOT_IN_PLAN":!restaurantEnabled?"RESTAURANT_DISABLED":"ENABLED";
    return new Entitlement(rs.getString("feature_key"),rs.getString("display_name"),rs.getString("category"),
      available,included,restaurantEnabled,effective,reason);
   },selectedPlan,tenantId);
  return ApiResponse.success("Calculated entitlements (preview only)",new Snapshot(tenantId,planId,planName,state,eligible,features));
 }
}
