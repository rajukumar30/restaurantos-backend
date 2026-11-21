package com.restaurantos.modules.platform;

import com.restaurantos.common.api.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@RestController
@RequestMapping("/api/v1/platform/plans")
public class PlatformPlanFeaturesController {
 private final JdbcTemplate jdbc;
 public PlatformPlanFeaturesController(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public record PlanFeature(String key,String name,String category,boolean available,boolean included,List<String> dependencies){}
 public record Update(boolean included){}
 private static final Map<String,List<String>> DEPENDENCIES=Map.of(
  "GUEST_ORDERING",List.of("QR_MENU"),
  "KITCHEN_DISPLAY",List.of("GUEST_ORDERING"),
  "WAITER_DASHBOARD",List.of("GUEST_ORDERING"),
  "ORDER_TRACKING",List.of("GUEST_ORDERING"),
  "ORDER_HISTORY",List.of("GUEST_ORDERING"),
  "SERVICE_REQUESTS",List.of("QR_MENU"),
  "DINE_IN",List.of("GUEST_ORDERING"),
  "TAKEAWAY",List.of("GUEST_ORDERING"));
 private String planStatus(UUID id){
  List<String> result=jdbc.query("SELECT status FROM platform_subscription_plans WHERE id=?",
   (rs,i)->rs.getString(1),id);
  if(result.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Plan not found");
  return result.get(0);
 }
 private List<PlanFeature> load(UUID id){
  return jdbc.query("""
   SELECT f.feature_key,f.display_name,f.category,f.available,pf.enabled AS included
   FROM feature_catalog f LEFT JOIN platform_plan_features pf
   ON pf.feature_key=f.feature_key AND pf.plan_id=?
   ORDER BY f.category,f.display_name
   """,(rs,i)->new PlanFeature(rs.getString("feature_key"),rs.getString("display_name"),
   rs.getString("category"),rs.getBoolean("available"),rs.getBoolean("included"),
   DEPENDENCIES.getOrDefault(rs.getString("feature_key"),List.of())),id);
 }
 @GetMapping("/{id}/features")
 public ApiResponse<List<PlanFeature>> list(@PathVariable UUID id){
  planStatus(id);return ApiResponse.success("Plan feature configuration",load(id));
 }
 @PutMapping("/{id}/features/{key}")
 @Transactional
 public ApiResponse<List<PlanFeature>> update(@PathVariable UUID id,@PathVariable String key,
  @RequestBody Update request,Authentication authentication){
  if(authentication==null||!(authentication.getPrincipal() instanceof UUID))
   throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Authentication required");
  // Lock the plan before checking its state, so publishing cannot race with editing.
  List<String> locked=jdbc.query("SELECT status FROM platform_subscription_plans WHERE id=? FOR UPDATE",
   (rs,i)->rs.getString(1),id);
  if(locked.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Plan not found");
  if(!"DRAFT".equals(locked.get(0)))throw new ResponseStatusException(HttpStatus.CONFLICT,"Only draft plan features can be edited");
  if(request==null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Feature selection required");
  List<PlanFeature> features=load(id);
  PlanFeature selected=features.stream().filter(f->f.key().equals(key)).findFirst()
   .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Unknown feature"));
  if(request.included()&&!selected.available())throw new ResponseStatusException(HttpStatus.CONFLICT,"Feature is not available");
  Map<String,Boolean> proposed=new HashMap<>();
  for(PlanFeature f:features)proposed.put(f.key(),f.included());
  proposed.put(key,request.included());
  for(PlanFeature f:features){
   if(!proposed.get(f.key()))continue;
   for(String dependency:f.dependencies())
    if(!Boolean.TRUE.equals(proposed.get(dependency)))
     throw new ResponseStatusException(HttpStatus.CONFLICT,f.key()+" requires "+dependency);
  }
  jdbc.update("""
   INSERT INTO platform_plan_features(plan_id,feature_key,enabled)
   VALUES(?,?,?) ON CONFLICT(plan_id,feature_key) DO UPDATE
   SET enabled=EXCLUDED.enabled,updated_at=NOW()
   """,id,key,request.included());
  return ApiResponse.success("Draft plan features updated",load(id));
 }
}
