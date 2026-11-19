package com.restaurantos.modules.staff;

import com.restaurantos.common.api.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/staff-operations")
public class StaffOperationsController {
 private final JdbcTemplate jdbc;
 private final TenantAccessService access;
 public StaffOperationsController(JdbcTemplate jdbc,TenantAccessService access){this.jdbc=jdbc;this.access=access;}

 @GetMapping("/branches")
 public ApiResponse<List<Map<String,Object>>> branches(@PathVariable UUID tenantId,Authentication auth){
  if(auth==null||!(auth.getPrincipal() instanceof UUID userId))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Login required");
  List<Map<String,Object>> rows=jdbc.queryForList("""
   SELECT DISTINCT b.id,b.name FROM branches b
   JOIN tenant_users tu ON tu.tenant_id=b.tenant_id AND tu.user_id=? AND tu.status='ACTIVE'
   JOIN user_roles ur ON ur.tenant_id=tu.tenant_id AND ur.tenant_user_id=tu.id
    AND (ur.branch_id IS NULL OR ur.branch_id=b.id)
   JOIN role_permissions rp ON rp.role_id=ur.role_id
   JOIN permissions p ON p.id=rp.permission_id AND p.permission_key='orders:read'
   WHERE b.tenant_id=? AND b.status='ACTIVE'
   ORDER BY b.name
   """,userId,tenantId);
  return ApiResponse.success("Accessible branches",rows);
 }

 @GetMapping("/access")
 public ApiResponse<Map<String,Object>> access(@PathVariable UUID tenantId,@RequestParam UUID branchId,Authentication auth){
  access.requirePermission(auth,tenantId,branchId,"orders:read");
  access.requireBranchInTenant(tenantId,branchId);
  UUID userId=(UUID)auth.getPrincipal();
  List<String> roles=jdbc.query("""
   SELECT DISTINCT r.role_key FROM tenant_users tu
   JOIN user_roles ur ON ur.tenant_user_id=tu.id AND ur.tenant_id=tu.tenant_id
   JOIN roles r ON r.id=ur.role_id
   WHERE tu.tenant_id=? AND tu.user_id=? AND tu.status='ACTIVE'
   AND (ur.branch_id IS NULL OR ur.branch_id=?)
   """,(rs,i)->rs.getString(1),tenantId,userId,branchId);
  List<String> permissions=jdbc.query("""
   SELECT DISTINCT p.permission_key FROM tenant_users tu
   JOIN user_roles ur ON ur.tenant_user_id=tu.id AND ur.tenant_id=tu.tenant_id
   JOIN role_permissions rp ON rp.role_id=ur.role_id
   JOIN permissions p ON p.id=rp.permission_id
   WHERE tu.tenant_id=? AND tu.user_id=? AND tu.status='ACTIVE'
   AND (ur.branch_id IS NULL OR ur.branch_id=?)
   """,(rs,i)->rs.getString(1),tenantId,userId,branchId);
  return ApiResponse.success("Staff access loaded",Map.of("roles",roles,"permissions",permissions,"branchId",branchId));
 }

 public record KitchenStatusRequest(@NotNull UUID branchId,@NotNull String status){}
 @PatchMapping("/kitchen/orders/{orderId}/status")
 @Transactional
 public ApiResponse<Map<String,Object>> kitchenStatus(@PathVariable UUID tenantId,@PathVariable UUID orderId,
   @Valid @RequestBody KitchenStatusRequest request,Authentication auth){
  access.requirePermission(auth,tenantId,request.branchId(),"kitchen:operate");
  access.requireBranchInTenant(tenantId,request.branchId());
  String target=request.status().trim().toUpperCase(Locale.ROOT);
  String from=switch(target){
   case "ACCEPTED"->"NEW";
   case "PREPARING"->"ACCEPTED";
   case "READY"->"PREPARING";
   default->throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid kitchen transition");
  };
  int changed=jdbc.update("""
   UPDATE restaurant_orders SET status=?,updated_at=NOW()
   WHERE tenant_id=? AND branch_id=? AND id=? AND status=?
   """,target,tenantId,request.branchId(),orderId,from);
  if(changed==0)throw new ResponseStatusException(HttpStatus.CONFLICT,"Order was updated or is not in the expected state");
  return ApiResponse.success("Kitchen status updated",Map.of("orderId",orderId,"status",target));
 }
}
