package com.restaurantos.modules.staff.service.impl;
import com.restaurantos.modules.staff.TenantAccessService;
import com.restaurantos.modules.realtime.dto.RealtimeEvent;
import com.restaurantos.modules.realtime.service.RealtimeEventPublisher;
import com.restaurantos.modules.staff.dto.StaffBillingDto.PaymentRequest;
import com.restaurantos.modules.staff.repository.StaffBillingDao;
import com.restaurantos.modules.staff.service.StaffBillingService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.util.*;
@Service
public class StaffBillingServiceImpl implements StaffBillingService {
 private final RealtimeEventPublisher events;
 private final StaffBillingDao dao;
 private final TenantAccessService access;
 public StaffBillingServiceImpl(StaffBillingDao dao,TenantAccessService access,RealtimeEventPublisher events){this.dao=dao;this.access=access;this.events=events;}
 private UUID requireCashier(Authentication auth,UUID tenantId,UUID branchId){
  UUID userId=access.requirePermission(auth,tenantId,branchId,"payments:read");
  access.requireBranchInTenant(tenantId,branchId);
  if(!dao.cashier(tenantId,branchId,userId))
   throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Cashier or manager access required");
  return userId;
 }
 @Transactional(readOnly=true)
 public List<Map<String,Object>> bills(UUID tenantId,UUID branchId,Authentication auth){
  requireCashier(auth,tenantId,branchId);
  return dao.bills(tenantId,branchId);
 }
 @Transactional
 public Map<String,Object> record(UUID tenantId,UUID orderId,PaymentRequest request,Authentication auth){
  UUID userId=requireCashier(auth,tenantId,request.branchId());
  String method=request.method().trim().toUpperCase(Locale.ROOT);
  if(!List.of("CASH","UPI","CARD","OTHER").contains(method))
   throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Unsupported payment method");
  List<BigDecimal> totals=dao.lockServedOrder(tenantId,request.branchId(),orderId);
  if(totals.isEmpty())
   throw new ResponseStatusException(HttpStatus.CONFLICT,"Only served orders can be paid");
  BigDecimal paid=dao.paid(tenantId,request.branchId(),orderId);
  BigDecimal balance=totals.getFirst().subtract(paid==null?BigDecimal.ZERO:paid);
  if(request.amount().compareTo(balance)>0)
   throw new ResponseStatusException(HttpStatus.CONFLICT,"Payment exceeds outstanding balance");
  String reference=request.reference()==null||request.reference().isBlank()?null:request.reference().trim();
  UUID paymentId=UUID.randomUUID();
  dao.insertPayment(paymentId,tenantId,request.branchId(),orderId,request.amount(),method,reference,userId);
  events.publishBillingAfterCommit(RealtimeEvent.orderChanged("PAYMENT_RECORDED",tenantId,request.branchId(),orderId));
  return Map.of("paymentId",paymentId,"remainingBalance",balance.subtract(request.amount()),"method",method);
 }
}
