package com.restaurantos.modules.platform;

import com.restaurantos.common.api.ApiResponse;
import com.restaurantos.modules.platform.dto.PlatformEntitlementsDto.Snapshot;
import com.restaurantos.modules.platform.service.PlatformEntitlementsService;
import com.restaurantos.modules.staff.TenantAccessService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.UUID;

/** Read-only subscription capability snapshot for authorized restaurant owners. */
@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/entitlements")
public class RestaurantEntitlementsController {
 private final PlatformEntitlementsService entitlements;
 private final TenantAccessService access;
 public RestaurantEntitlementsController(PlatformEntitlementsService entitlements,TenantAccessService access){
  this.entitlements=entitlements;this.access=access;
 }
 @GetMapping
 public ApiResponse<Snapshot> snapshot(@PathVariable UUID tenantId,Authentication authentication){
  if(!access.hasTenantWidePermission(authentication,tenantId,"orders:read"))
   throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Restaurant-wide permission required");
  return ApiResponse.success("Restaurant feature entitlements",entitlements.snapshot(tenantId));
 }
}
