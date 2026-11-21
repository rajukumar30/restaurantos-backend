package com.restaurantos.modules.orders.service.impl;
import com.restaurantos.modules.orders.dto.OrderDto.*;
import com.restaurantos.modules.orders.repository.OrderQueryDao;
import com.restaurantos.modules.orders.service.OrderQueryService;
import com.restaurantos.modules.staff.TenantAccessService;
import com.restaurantos.modules.platform.RestaurantFeatureAccessService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
@Service
public class OrderQueryServiceImpl implements OrderQueryService {
 private final OrderQueryDao dao;
 private final TenantAccessService access;
 private final RestaurantFeatureAccessService featureAccess;
 public OrderQueryServiceImpl(OrderQueryDao dao,TenantAccessService access,RestaurantFeatureAccessService featureAccess){this.dao=dao;this.access=access;this.featureAccess=featureAccess;}
 @Transactional(readOnly=true)
 public PublicOrderStatus publicStatus(String qrToken,UUID orderId){
  List<PublicOrderStatus> results=dao.publicStatus(qrToken,orderId);
  if(results.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Order not found for this table.");
  return results.getFirst();
 }
 @Transactional(readOnly=true)
 public List<OrderSummary> list(UUID tenantId,UUID branchId,Authentication auth){
  access.requirePermission(auth,tenantId,branchId,"orders:read");
  access.requireBranchInTenant(tenantId,branchId);
  featureAccess.require(tenantId,"GUEST_ORDERING");
  return dao.list(tenantId,branchId);
 }
 @Transactional
 public OrderStatusUpdated updateStatus(UUID tenantId,UUID orderId,UpdateOrderStatusRequest request,Authentication auth){
  access.requirePermission(auth,tenantId,request.branchId(),"orders:manage");
  access.requireBranchInTenant(tenantId,request.branchId());
  featureAccess.require(tenantId,"GUEST_ORDERING");
  String status=request.status().trim().toUpperCase(Locale.ROOT);
  if(!List.of("SERVED","CANCELLED").contains(status))
   throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Unsupported order status.");
  if(dao.updateStatus(tenantId,request.branchId(),orderId,status)==0)
   throw new ResponseStatusException(HttpStatus.CONFLICT,"Order status changed or this transition is not allowed.");
  if("SERVED".equals(status)){
   UUID actorId=(UUID)auth.getPrincipal();
   dao.recordServed(tenantId,request.branchId(),orderId,actorId);
  }
  return new OrderStatusUpdated(orderId,status);
 }
}
