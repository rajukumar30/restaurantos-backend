package com.restaurantos.modules.service;
import com.restaurantos.modules.dining.service.DiningSessionService;
import org.springframework.transaction.annotation.Transactional;
import com.restaurantos.common.api.ApiResponse;
import com.restaurantos.modules.service.dto.ServiceRequestDto.*;
import com.restaurantos.modules.service.service.ServiceRequestService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController
public class ServiceRequestController {
 private final ServiceRequestService service;private final DiningSessionService dining;
 public ServiceRequestController(ServiceRequestService service,DiningSessionService dining){this.service=service;this.dining=dining;}
 @GetMapping("/api/v1/public/qr/{qrToken}/service-requests/availability")
 public ApiResponse<Boolean> availability(@PathVariable String qrToken){
  return ApiResponse.success("Service request availability",service.availability(qrToken));
 }
 @Transactional
 @PostMapping("/api/v1/public/qr/{qrToken}/service-requests")
 public ApiResponse<ServiceRequest> create(@PathVariable String qrToken,@Valid @RequestBody CreateRequest request,@RequestHeader(value="X-Dining-Session",required=false) String session){
  dining.require(qrToken,session,true,false);
  return ApiResponse.success("Staff has been notified",service.create(qrToken,request));
 }
 @Transactional
 @GetMapping("/api/v1/public/qr/{qrToken}/service-requests")
 public ApiResponse<List<ServiceRequest>> guestList(@PathVariable String qrToken,@RequestHeader(value="X-Dining-Session",required=false) String session){
  dining.require(qrToken,session,false,false);
  return ApiResponse.success("Active service requests",service.guestList(qrToken));
 }
 @Transactional
 @PatchMapping("/api/v1/public/qr/{qrToken}/service-requests/{requestId}/cancel")
 public ApiResponse<ServiceRequest> cancel(@PathVariable String qrToken,@PathVariable UUID requestId,@RequestHeader(value="X-Dining-Session",required=false) String session){
  dining.require(qrToken,session,true,false);
  return ApiResponse.success("Request cancelled",service.cancel(qrToken,requestId));
 }
 @GetMapping("/api/v1/tenants/{tenantId}/service-requests")
 public ApiResponse<List<ServiceRequest>> staffList(@PathVariable UUID tenantId,@RequestParam UUID branchId,Authentication auth){
  return ApiResponse.success("Service requests",service.staffList(tenantId,branchId,auth));
 }
 @PatchMapping("/api/v1/tenants/{tenantId}/service-requests/{requestId}")
 public ApiResponse<ServiceRequest> update(@PathVariable UUID tenantId,@PathVariable UUID requestId,
  @RequestParam UUID branchId,@Valid @RequestBody UpdateRequest request,Authentication auth){
  return ApiResponse.success("Service request updated",service.update(tenantId,requestId,branchId,request,auth));
 }
}
