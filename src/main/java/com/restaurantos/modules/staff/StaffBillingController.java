package com.restaurantos.modules.staff;
import com.restaurantos.common.api.ApiResponse;
import com.restaurantos.modules.staff.dto.StaffBillingDto.PaymentRequest;
import com.restaurantos.modules.staff.service.StaffBillingService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/staff-operations/billing")
public class StaffBillingController {
 private final StaffBillingService service;
 public StaffBillingController(StaffBillingService service){this.service=service;}
 @GetMapping("/orders")
 public ApiResponse<List<Map<String,Object>>> bills(@PathVariable UUID tenantId,@RequestParam UUID branchId,Authentication auth){
  return ApiResponse.success("Bills loaded",service.bills(tenantId,branchId,auth));
 }
 @PostMapping("/orders/{orderId}/payments")
 public ApiResponse<Map<String,Object>> record(@PathVariable UUID tenantId,@PathVariable UUID orderId,
  @Valid @RequestBody PaymentRequest request,Authentication auth){
  return ApiResponse.success("Manual payment recorded",service.record(tenantId,orderId,request,auth));
 }
}
