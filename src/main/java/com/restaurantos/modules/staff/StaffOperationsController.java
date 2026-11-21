package com.restaurantos.modules.staff;
import com.restaurantos.common.api.ApiResponse;
import com.restaurantos.modules.staff.dto.StaffOperationsDto.KitchenStatusRequest;
import com.restaurantos.modules.staff.service.StaffOperationsService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/staff-operations")
public class StaffOperationsController {
 private final StaffOperationsService service;
 public StaffOperationsController(StaffOperationsService service){this.service=service;}
 @GetMapping("/branches")
 public ApiResponse<List<Map<String,Object>>> branches(@PathVariable UUID tenantId,Authentication auth){
  return ApiResponse.success("Accessible branches",service.branches(tenantId,auth));
 }
 @GetMapping("/access")
 public ApiResponse<Map<String,Object>> access(@PathVariable UUID tenantId,@RequestParam UUID branchId,Authentication auth){
  return ApiResponse.success("Staff access loaded",service.access(tenantId,branchId,auth));
 }
 @GetMapping("/service-mode")
 public ApiResponse<Map<String,Object>> serviceMode(@PathVariable UUID tenantId,@RequestParam UUID branchId,Authentication auth){
  return ApiResponse.success("Service mode loaded",service.serviceMode(tenantId,branchId,auth));
 }
 @PatchMapping("/kitchen/orders/{orderId}/served")
 public ApiResponse<Map<String,Object>> kitchenServed(@PathVariable UUID tenantId,@PathVariable UUID orderId,
  @Valid @RequestBody KitchenStatusRequest request,Authentication auth){
  return ApiResponse.success("Order handed off and marked served",service.kitchenServed(tenantId,orderId,request,auth));
 }
 @PatchMapping("/kitchen/orders/{orderId}/status")
 public ApiResponse<Map<String,Object>> kitchenStatus(@PathVariable UUID tenantId,@PathVariable UUID orderId,
  @Valid @RequestBody KitchenStatusRequest request,Authentication auth){
  return ApiResponse.success("Kitchen status updated",service.kitchenStatus(tenantId,orderId,request,auth));
 }
}
