package com.restaurantos.modules.platform;
import com.restaurantos.common.api.ApiResponse;
import com.restaurantos.modules.platform.dto.PlatformEntitlementsDto.Snapshot;
import com.restaurantos.modules.platform.service.PlatformEntitlementsService;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
@RestController
@RequestMapping("/api/v1/platform/restaurants")
public class PlatformEntitlementsController {
 private final PlatformEntitlementsService service;
 public PlatformEntitlementsController(PlatformEntitlementsService service){this.service=service;}
 @GetMapping("/{tenantId}/entitlements")
 public ApiResponse<Snapshot> snapshot(@PathVariable UUID tenantId){
  return ApiResponse.success("Calculated entitlements (preview only)",service.snapshot(tenantId));
 }
}
