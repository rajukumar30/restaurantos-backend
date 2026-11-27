package com.restaurantos.modules.staff;

import com.restaurantos.common.api.ApiResponse;
import com.restaurantos.modules.auth.repository.AuthDao;
import com.restaurantos.modules.platform.TenantFeatureAccess;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

/** Staff-safe feature availability, without exposing subscription or billing data. */
@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/staff-operations/features")
public class StaffFeatureController {
 private static final List<String> KEYS=List.of("KITCHEN_DISPLAY","WAITER_DASHBOARD","GUEST_ORDERING","QR_MENU","SERVICE_REQUESTS");
 private final AuthDao auth;
 private final TenantFeatureAccess features;
 public StaffFeatureController(AuthDao auth,TenantFeatureAccess features){this.auth=auth;this.features=features;}
 public record Feature(String key,boolean effectiveEnabled){}
 public record FeatureAccess(List<Feature> features){}
 @GetMapping
 public ApiResponse<FeatureAccess> available(@PathVariable UUID tenantId,Authentication authentication){
  if(authentication==null||!(authentication.getPrincipal() instanceof UUID userId)
      ||auth.roleKeys(userId,tenantId).isEmpty())
   throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Account disabled or access denied");
  Set<String> enabled=features.enabledFeatures(tenantId,KEYS);
  return ApiResponse.success("Staff features",new FeatureAccess(KEYS.stream()
   .map(key->new Feature(key,enabled.contains(key))).toList()));
 }
}
