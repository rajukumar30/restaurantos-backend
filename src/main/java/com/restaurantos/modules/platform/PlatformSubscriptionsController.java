package com.restaurantos.modules.platform;

import com.restaurantos.common.api.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/platform/subscriptions")
public class PlatformSubscriptionsController {
 private final JdbcTemplate jdbc;
 public PlatformSubscriptionsController(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public record Subscription(UUID id,UUID tenantId,String restaurantName,UUID planId,String planName,
                            String billingCycle,LocalDate startsOn,LocalDate freeUntil,LocalDate endsOn,
                            String status,boolean freeAccessNow,String lifecycleStatus){}
 public record Assign(UUID tenantId,UUID planId,String billingCycle,LocalDate startsOn,LocalDate freeUntil,LocalDate endsOn){}
 private Subscription map(java.sql.ResultSet rs)throws java.sql.SQLException{
  LocalDate start=rs.getObject("starts_on",LocalDate.class);
  LocalDate free=rs.getObject("free_until",LocalDate.class);
  LocalDate end=rs.getObject("ends_on",LocalDate.class);
  LocalDate today=LocalDate.now(java.time.ZoneOffset.UTC);
  boolean withinTerm=!today.isBefore(start)&&(end==null||!today.isAfter(end));
  boolean freeNow="ACTIVE".equals(rs.getString("status"))&&withinTerm&&free!=null&&!today.isAfter(free);
  String lifecycle="CANCELLED".equals(rs.getString("status"))?"CANCELLED":today.isBefore(start)?"SCHEDULED":end!=null&&today.isAfter(end)?"EXPIRED":freeNow?"FREE_PERIOD":"ACTIVE";
  return new Subscription(rs.getObject("id",UUID.class),rs.getObject("tenant_id",UUID.class),
   rs.getString("restaurant_name"),rs.getObject("plan_id",UUID.class),rs.getString("plan_name"),
   rs.getString("billing_cycle"),start,free,end,rs.getString("status"),freeNow,lifecycle);
 }
 private static final String QUERY="""
  SELECT s.id,s.tenant_id,t.name AS restaurant_name,s.plan_id,p.name AS plan_name,
  s.billing_cycle,s.starts_on,s.free_until,s.ends_on,s.status
  FROM platform_restaurant_subscriptions s
  JOIN tenants t ON t.id=s.tenant_id
  JOIN platform_subscription_plans p ON p.id=s.plan_id
  """;
 @GetMapping
 public ApiResponse<List<Subscription>> list(@RequestParam(required=false) UUID tenantId){
  return ApiResponse.success("Restaurant subscriptions",jdbc.query(
   QUERY+" WHERE (?::uuid IS NULL OR s.tenant_id=?::uuid) ORDER BY s.created_at DESC LIMIT 200",
   (rs,i)->map(rs),tenantId==null?null:tenantId.toString(),tenantId==null?null:tenantId.toString()));
 }
 @PostMapping
 @Transactional
 public ApiResponse<Subscription> assign(@RequestBody Assign req,Authentication authentication){
  if(authentication==null||!(authentication.getPrincipal() instanceof UUID actor))
   throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Authentication required");
  if(req.tenantId()==null||req.planId()==null||req.startsOn()==null||
    !("MONTHLY".equals(req.billingCycle())||"ANNUAL".equals(req.billingCycle()))||
    (req.freeUntil()!=null&&req.freeUntil().isBefore(req.startsOn()))||
    (req.endsOn()!=null&&req.endsOn().isBefore(req.startsOn()))||
    (req.freeUntil()!=null&&req.endsOn()!=null&&req.freeUntil().isAfter(req.endsOn())))
   throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid subscription dates or billing cycle");
  List<String> plans=jdbc.query("SELECT status FROM platform_subscription_plans WHERE id=?",
   (rs,i)->rs.getString(1),req.planId());
  if(plans.isEmpty()||!"ACTIVE".equals(plans.get(0)))
   throw new ResponseStatusException(HttpStatus.CONFLICT,"Select an active plan");
  List<UUID> restaurants=jdbc.query("SELECT id FROM tenants WHERE id=?",
   (rs,i)->rs.getObject(1,UUID.class),req.tenantId());
  if(restaurants.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Restaurant not found");
  // Serialize subscription changes for this restaurant to avoid two simultaneous active assignments.
  jdbc.queryForObject("SELECT id FROM tenants WHERE id=? FOR UPDATE",UUID.class,req.tenantId());
  jdbc.update("UPDATE platform_restaurant_subscriptions SET status='CANCELLED',updated_at=NOW() WHERE tenant_id=? AND status='ACTIVE'",req.tenantId());
  UUID id=jdbc.queryForObject("""
   INSERT INTO platform_restaurant_subscriptions(tenant_id,plan_id,billing_cycle,starts_on,free_until,ends_on)
   VALUES(?,?,?,?,?,?) RETURNING id
   """,UUID.class,req.tenantId(),req.planId(),req.billingCycle(),req.startsOn(),req.freeUntil(),req.endsOn());
  jdbc.update("INSERT INTO platform_admin_audit_logs(actor_user_id,tenant_id,action) VALUES(?,?,'SUBSCRIPTION_ASSIGNED')",
   actor,req.tenantId());
  return ApiResponse.success("Restaurant subscription assigned",jdbc.queryForObject(
   QUERY+" WHERE s.id=?",(rs,i)->map(rs),id));
 }

 @PostMapping("/{id}/cancel")
 @Transactional
 public ApiResponse<Subscription> cancel(@PathVariable UUID id,Authentication authentication){
  if(authentication==null||!(authentication.getPrincipal() instanceof UUID actor))
   throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Authentication required");
  List<UUID> tenants=jdbc.query("SELECT tenant_id FROM platform_restaurant_subscriptions WHERE id=?",
   (rs,i)->rs.getObject(1,UUID.class),id);
  if(tenants.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Subscription not found");
  UUID tenantId=tenants.get(0);
  jdbc.queryForObject("SELECT id FROM tenants WHERE id=? FOR UPDATE",UUID.class,tenantId);
  int updated=jdbc.update("""
   UPDATE platform_restaurant_subscriptions SET status='CANCELLED',updated_at=NOW()
   WHERE id=? AND status='ACTIVE'
   """,id);
  if(updated==0)throw new ResponseStatusException(HttpStatus.CONFLICT,"Subscription already cancelled");
  jdbc.update("INSERT INTO platform_admin_audit_logs(actor_user_id,tenant_id,action) VALUES(?,?,'SUBSCRIPTION_CANCELLED')",
   actor,tenantId);
  return ApiResponse.success("Subscription cancelled",jdbc.queryForObject(QUERY+" WHERE s.id=?",(rs,i)->map(rs),id));
 }
}
