package com.restaurantos.modules.platform;

import com.restaurantos.common.api.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/platform/plans")
public class PlatformPlansController {
 private final JdbcTemplate jdbc;
 public PlatformPlansController(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public record Plan(UUID id,String name,String description,long monthlyPricePaise,long annualPricePaise,String status,OffsetDateTime createdAt){}
 public record CreatePlan(String name,String description,Long monthlyPricePaise,Long annualPricePaise){}
 private Plan map(java.sql.ResultSet rs)throws java.sql.SQLException{
  return new Plan(rs.getObject("id",UUID.class),rs.getString("name"),rs.getString("description"),
   rs.getLong("monthly_price_paise"),rs.getLong("annual_price_paise"),rs.getString("status"),
   rs.getObject("created_at",OffsetDateTime.class));
 }
 @GetMapping
 public ApiResponse<List<Plan>> list(){
  return ApiResponse.success("Subscription plans",jdbc.query("""
   SELECT id,name,description,monthly_price_paise,annual_price_paise,status,created_at
   FROM platform_subscription_plans ORDER BY created_at DESC,id DESC
   """,(rs,i)->map(rs)));
 }
 @PostMapping
 @Transactional
 public ApiResponse<Plan> create(@RequestBody CreatePlan request,Authentication authentication){
  if(authentication==null||!(authentication.getPrincipal() instanceof UUID))
   throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Authentication required");
  String name=request.name()==null?"":request.name().strip();
  String description=request.description()==null?"":request.description().strip();
  if(name.isBlank()||name.length()>100||description.length()>500)
   throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid plan name or description");
  if(request.monthlyPricePaise()==null||request.annualPricePaise()==null||
    request.monthlyPricePaise()<0||request.annualPricePaise()<0)
   throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Prices must be nonnegative");
  try{
   UUID id=jdbc.queryForObject("""
    INSERT INTO platform_subscription_plans(name,description,monthly_price_paise,annual_price_paise)
    VALUES(?,?,?,?) RETURNING id
    """,UUID.class,name,description,request.monthlyPricePaise(),request.annualPricePaise());
   Plan plan=jdbc.queryForObject("""
    SELECT id,name,description,monthly_price_paise,annual_price_paise,status,created_at
    FROM platform_subscription_plans WHERE id=?
    """,(rs,i)->map(rs),id);
   return ApiResponse.success("Draft plan created",plan);
  }catch(org.springframework.dao.DuplicateKeyException ex){
   throw new ResponseStatusException(HttpStatus.CONFLICT,"A plan with this name already exists");
  }
 }

 @PatchMapping("/{id}")
 @Transactional
 public ApiResponse<Plan> update(@PathVariable UUID id,@RequestBody CreatePlan request,Authentication authentication){
  if(authentication==null||!(authentication.getPrincipal() instanceof UUID))
   throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Authentication required");
  String name=request.name()==null?"":request.name().strip();
  String description=request.description()==null?"":request.description().strip();
  if(name.isBlank()||name.length()>100||description.length()>500||
     request.monthlyPricePaise()==null||request.annualPricePaise()==null||
     request.monthlyPricePaise()<0||request.annualPricePaise()<0)
   throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid plan details or pricing");
  try{
   int count=jdbc.update("""
    UPDATE platform_subscription_plans
    SET name=?,description=?,monthly_price_paise=?,annual_price_paise=?,updated_at=NOW()
    WHERE id=? AND status='DRAFT'
    """,name,description,request.monthlyPricePaise(),request.annualPricePaise(),id);
   if(count==0)throw new ResponseStatusException(HttpStatus.CONFLICT,"Only draft plans can be edited");
   return ApiResponse.success("Plan updated",find(id));
  }catch(org.springframework.dao.DuplicateKeyException ex){
   throw new ResponseStatusException(HttpStatus.CONFLICT,"A plan with this name already exists");
  }
 }
 @PostMapping("/{id}/deactivate")
 @Transactional
 public ApiResponse<Plan> deactivate(@PathVariable UUID id,Authentication authentication){
  if(authentication==null||!(authentication.getPrincipal() instanceof UUID))
   throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Authentication required");
  int count=jdbc.update("""
   UPDATE platform_subscription_plans SET status='ARCHIVED',updated_at=NOW()
   WHERE id=? AND status IN ('DRAFT','ACTIVE')
   """,id);
  if(count==0)throw new ResponseStatusException(HttpStatus.CONFLICT,"Plan not found or already deactivated");
  return ApiResponse.success("Plan deactivated",find(id));
 }
 private Plan find(UUID id){
  return jdbc.queryForObject("""
   SELECT id,name,description,monthly_price_paise,annual_price_paise,status,created_at
   FROM platform_subscription_plans WHERE id=?
   """,(rs,i)->map(rs),id);
 }
}
