package com.restaurantos.modules.platform;
import com.restaurantos.common.api.ApiResponse;
import com.restaurantos.modules.platform.dto.PlatformFeatureGrantDto.*;
import com.restaurantos.modules.platform.service.PlatformFeatureGrantService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController
@RequestMapping("/api/v1/platform/restaurants/{tenantId}/grants")
public class PlatformFeatureGrantsController {
 private final PlatformFeatureGrantService service;
 public PlatformFeatureGrantsController(PlatformFeatureGrantService service){this.service=service;}
 @GetMapping public ApiResponse<List<Grant>> list(@PathVariable UUID tenantId){
  return ApiResponse.success("Feature grants",service.list(tenantId));}
 @PostMapping public ApiResponse<List<Grant>> create(@PathVariable UUID tenantId,
  @Valid @RequestBody Create request,Authentication authentication){
  return ApiResponse.success("Complimentary grant created",service.create(tenantId,request,authentication));}
 @DeleteMapping("/{grantId}") public ApiResponse<List<Grant>> revoke(@PathVariable UUID tenantId,
  @PathVariable UUID grantId,Authentication authentication){
  return ApiResponse.success("Grant revoked",service.revoke(tenantId,grantId,authentication));}
}
