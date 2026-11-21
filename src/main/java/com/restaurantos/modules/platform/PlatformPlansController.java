package com.restaurantos.modules.platform;

import com.restaurantos.common.api.ApiResponse;
import com.restaurantos.modules.subscription.dto.PlanRequest;
import com.restaurantos.modules.subscription.entity.SubscriptionPlan;
import com.restaurantos.modules.subscription.service.PlanService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/platform/plans")
public class PlatformPlansController {
 private final PlanService service;
 private final com.restaurantos.modules.subscription.repository.SubscriptionPlanDao planDao;
 public PlatformPlansController(PlanService service,com.restaurantos.modules.subscription.repository.SubscriptionPlanDao planDao){this.service=service;this.planDao=planDao;}
 private void requireAdmin(Authentication authentication){
  if(authentication==null||!(authentication.getPrincipal() instanceof UUID))
   throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Authentication required");
 }
 @GetMapping
 public ApiResponse<List<SubscriptionPlan>> list(){return ApiResponse.success("Subscription plans",service.list());}
 @PostMapping
 public ApiResponse<SubscriptionPlan> create(@RequestBody PlanRequest request,Authentication authentication){
  requireAdmin(authentication);return ApiResponse.success("Draft plan created",service.create(request));
 }
 @PatchMapping("/{id}")
 public ApiResponse<SubscriptionPlan> update(@PathVariable UUID id,@RequestBody PlanRequest request,Authentication authentication){
  requireAdmin(authentication);return ApiResponse.success("Plan updated",service.update(id,request));
 }
 @GetMapping("/{id}/feature-editable")
 public ApiResponse<Boolean> featureEditable(@PathVariable UUID id,Authentication authentication){
  requireAdmin(authentication);
  var plan=planDao.findById(id);
  if(plan==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Plan not found");
  return ApiResponse.success("Feature edit permission", "DRAFT".equals(plan.status()) || ("ARCHIVED".equals(plan.status()) && !planDao.hasSubscriptions(id)));
 }
 @PostMapping("/{id}/activate")
 public ApiResponse<SubscriptionPlan> activate(@PathVariable UUID id,Authentication authentication){
  requireAdmin(authentication);return ApiResponse.success("Plan activated",service.activate(id));
 }
 @PostMapping("/{id}/reactivate")
 public ApiResponse<SubscriptionPlan> reactivate(@PathVariable UUID id,Authentication authentication){
  requireAdmin(authentication);return ApiResponse.success("Plan reactivated",service.reactivate(id));
 }
 @PostMapping("/{id}/deactivate")
 public ApiResponse<SubscriptionPlan> deactivate(@PathVariable UUID id,Authentication authentication){
  requireAdmin(authentication);return ApiResponse.success("Plan deactivated",service.deactivate(id));
 }
}
