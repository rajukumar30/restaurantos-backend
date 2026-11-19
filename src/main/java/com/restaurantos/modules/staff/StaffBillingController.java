package com.restaurantos.modules.staff;

import com.restaurantos.common.api.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.util.*;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/staff-operations/billing")
public class StaffBillingController {
 private final JdbcTemplate jdbc;
 private final TenantAccessService access;
 public StaffBillingController(JdbcTemplate jdbc,TenantAccessService access){this.jdbc=jdbc;this.access=access;}

 private UUID requireCashier(Authentication auth,UUID tenantId,UUID branchId){
  UUID userId=access.requirePermission(auth,tenantId,branchId,"payments:read");
  access.requireBranchInTenant(tenantId,branchId);
  Integer count=jdbc.queryForObject("""
   SELECT COUNT(*) FROM tenant_users tu
   JOIN user_roles ur ON ur.tenant_id=tu.tenant_id AND ur.tenant_user_id=tu.id
   JOIN roles r ON r.id=ur.role_id
   WHERE tu.tenant_id=? AND tu.user_id=? AND tu.status='ACTIVE'
   AND (ur.branch_id IS NULL OR ur.branch_id=?)
   AND r.role_key IN ('CASHIER','MANAGER','OWNER')
   """,Integer.class,tenantId,userId,branchId);
  if(count==null||count==0)throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Cashier or manager access required");
  return userId;
 }
 @GetMapping("/orders")
 public ApiResponse<List<Map<String,Object>>> bills(@PathVariable UUID tenantId,@RequestParam UUID branchId,Authentication auth){
  requireCashier(auth,tenantId,branchId);
  return ApiResponse.success("Bills loaded",jdbc.queryForList("""
   SELECT o.id,o.order_number AS "orderNumber",rt.display_name AS "tableName",
   o.status,o.total_amount AS "totalAmount",
   COALESCE((SELECT SUM(p.amount) FROM restaurant_order_payments p
     WHERE p.tenant_id=o.tenant_id AND p.branch_id=o.branch_id AND p.order_id=o.id),0) AS "paidAmount",
   o.created_at AS "createdAt"
   FROM restaurant_orders o
   JOIN restaurant_tables rt ON rt.id=o.table_id AND rt.tenant_id=o.tenant_id
   WHERE o.tenant_id=? AND o.branch_id=? AND o.status IN ('SERVED','READY')
   ORDER BY o.created_at DESC LIMIT 100
   """,tenantId,branchId));
 }
 public record PaymentRequest(@NotNull UUID branchId,@NotNull @DecimalMin("0.01") BigDecimal amount,
   @NotBlank String method,@Size(max=120) String reference){}
 @PostMapping("/orders/{orderId}/payments")
 @Transactional
 public ApiResponse<Map<String,Object>> record(@PathVariable UUID tenantId,@PathVariable UUID orderId,
   @Valid @RequestBody PaymentRequest request,Authentication auth){
  UUID userId=requireCashier(auth,tenantId,request.branchId());
  String method=request.method().trim().toUpperCase(Locale.ROOT);
  if(!List.of("CASH","UPI","CARD","OTHER").contains(method))
   throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Unsupported payment method");
  List<BigDecimal> totals=jdbc.query("""
   SELECT total_amount FROM restaurant_orders
   WHERE tenant_id=? AND branch_id=? AND id=? AND status='SERVED' FOR UPDATE
   """,(rs,i)->rs.getBigDecimal(1),tenantId,request.branchId(),orderId);
  if(totals.isEmpty())throw new ResponseStatusException(HttpStatus.CONFLICT,"Only served orders can be paid");
  BigDecimal paid=jdbc.queryForObject("""
   SELECT COALESCE(SUM(amount),0) FROM restaurant_order_payments
   WHERE tenant_id=? AND branch_id=? AND order_id=?
   """,BigDecimal.class,tenantId,request.branchId(),orderId);
  BigDecimal balance=totals.get(0).subtract(paid==null?BigDecimal.ZERO:paid);
  if(request.amount().compareTo(balance)>0)
   throw new ResponseStatusException(HttpStatus.CONFLICT,"Payment exceeds outstanding balance");
  String reference=request.reference()==null||request.reference().isBlank()?null:request.reference().trim();
  UUID paymentId=UUID.randomUUID();
  jdbc.update("""
   INSERT INTO restaurant_order_payments(id,tenant_id,branch_id,order_id,amount,method,reference,recorded_by)
   VALUES (?,?,?,?,?,?,?,?)
   """,paymentId,tenantId,request.branchId(),orderId,request.amount(),method,reference,userId);
  return ApiResponse.success("Manual payment recorded",Map.of("paymentId",paymentId,
   "remainingBalance",balance.subtract(request.amount()),"method",method));
 }
}
