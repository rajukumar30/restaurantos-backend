package com.restaurantos.modules.staff;
import com.restaurantos.common.api.ApiResponse;
import com.restaurantos.modules.staff.dto.ServiceModeDto.ServiceModeRequest;
import com.restaurantos.modules.staff.service.PlatformServiceModeService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController
@RequestMapping("/api/v1/platform/restaurant-service")
public class PlatformServiceModeController {
 private final PlatformServiceModeService service;
 public PlatformServiceModeController(PlatformServiceModeService service){this.service=service;}
 @GetMapping("/{tenantId}")
 public ApiResponse<List<Map<String,Object>>> list(@PathVariable UUID tenantId,Authentication auth){
  return ApiResponse.success("Branch service modes",service.list(tenantId,auth));
 }
 @PutMapping("/{tenantId}")
 public ApiResponse<Map<String,Object>> set(@PathVariable UUID tenantId,@Valid @RequestBody ServiceModeRequest request,Authentication auth){
  return ApiResponse.success("Service mode updated",service.set(tenantId,request,auth));
 }
}
