package com.restaurantos.modules.staff.service.impl;
import com.restaurantos.modules.staff.TenantAccessService;
import com.restaurantos.modules.platform.TenantFeatureAccess;
import com.restaurantos.modules.realtime.dto.RealtimeEvent;
import com.restaurantos.modules.realtime.service.RealtimeEventPublisher;
import com.restaurantos.modules.staff.dto.StaffOperationsDto.KitchenStatusRequest;
import com.restaurantos.modules.staff.repository.StaffOperationsDao;
import com.restaurantos.modules.staff.service.StaffOperationsService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
@Service
public class StaffOperationsServiceImpl implements StaffOperationsService {
 private final StaffOperationsDao dao;
 private final TenantAccessService access;
 private final TenantFeatureAccess features;
 private final RealtimeEventPublisher events;
 public StaffOperationsServiceImpl(StaffOperationsDao dao,TenantAccessService access,TenantFeatureAccess features,RealtimeEventPublisher events){this.dao=dao;this.access=access;this.features=features;this.events=events;}
 @Transactional(readOnly=true)
 public List<Map<String,Object>> branches(UUID tenantId,Authentication auth){
  if(auth==null||!(auth.getPrincipal() instanceof UUID userId))
   throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Login required");
  return dao.branches(userId,tenantId);
 }
 private void authorize(UUID tenantId,UUID branchId,Authentication auth){
  access.requirePermission(auth,tenantId,branchId,"orders:read");
  access.requireBranchInTenant(tenantId,branchId);
 }
 @Transactional(readOnly=true)
 public Map<String,Object> access(UUID tenantId,UUID branchId,Authentication auth){
  authorize(tenantId,branchId,auth);
  UUID userId=(UUID)auth.getPrincipal();
  return Map.of("roles",dao.roles(tenantId,userId,branchId),
   "permissions",dao.permissions(tenantId,userId,branchId),"branchId",branchId);
 }
 @Transactional(readOnly=true)
 public Map<String,Object> serviceMode(UUID tenantId,UUID branchId,Authentication auth){
  authorize(tenantId,branchId,auth);
  return Map.of("mode",dao.mode(tenantId,branchId));
 }
 @Transactional
 public Map<String,Object> kitchenServed(UUID tenantId,UUID orderId,KitchenStatusRequest request,Authentication auth){
  UUID userId=access.requirePermission(auth,tenantId,request.branchId(),"kitchen:operate");
  access.requireBranchInTenant(tenantId,request.branchId());
  features.require(tenantId,"KITCHEN_DISPLAY");
  if(!"KITCHEN_MANAGED".equals(dao.mode(tenantId,request.branchId())))
   throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Kitchen handoff is not enabled for this branch");
  if(dao.updateStatus(tenantId,request.branchId(),orderId,"SERVED","READY")==0)
   throw new ResponseStatusException(HttpStatus.CONFLICT,"Order must be READY before kitchen handoff");
  dao.recordKitchenHandoff(tenantId,request.branchId(),orderId,userId);
  events.publishAfterCommit(RealtimeEvent.orderChanged("ORDER_STATUS_CHANGED",tenantId,request.branchId(),orderId));
  return Map.of("orderId",orderId,"status","SERVED");
 }
 @Transactional
 public Map<String,Object> kitchenStatus(UUID tenantId,UUID orderId,KitchenStatusRequest request,Authentication auth){
  access.requirePermission(auth,tenantId,request.branchId(),"kitchen:operate");
  access.requireBranchInTenant(tenantId,request.branchId());
  features.require(tenantId,"KITCHEN_DISPLAY");
  String target=request.status().trim().toUpperCase(Locale.ROOT);
  String from=switch(target){
   case "ACCEPTED"->"NEW";
   case "PREPARING"->"ACCEPTED";
   case "READY"->"PREPARING";
   default->throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid kitchen transition");
  };
  if(dao.updateStatus(tenantId,request.branchId(),orderId,target,from)==0)
   throw new ResponseStatusException(HttpStatus.CONFLICT,"Order was updated or is not in the expected state");
  events.publishAfterCommit(RealtimeEvent.orderChanged("KITCHEN_STATUS_CHANGED",tenantId,request.branchId(),orderId));
  return Map.of("orderId",orderId,"status",target);
 }
}
