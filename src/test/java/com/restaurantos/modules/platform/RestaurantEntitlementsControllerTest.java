package com.restaurantos.modules.platform;

import com.restaurantos.modules.platform.service.PlatformEntitlementsService;
import com.restaurantos.modules.staff.TenantAccessService;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RestaurantEntitlementsControllerTest {
 private final PlatformEntitlementsService entitlements=mock(PlatformEntitlementsService.class);
 private final TenantAccessService access=mock(TenantAccessService.class);
 private final RestaurantEntitlementsController controller=new RestaurantEntitlementsController(entitlements,access);
 private final Authentication auth=mock(Authentication.class);
 @Test void deniesCrossTenantAccessWithoutLoadingFeatures(){
  UUID tenant=UUID.randomUUID();
  assertThrows(ResponseStatusException.class,()->controller.snapshot(tenant,auth));
  verify(access).hasTenantWidePermission(auth,tenant,"orders:read");
  verifyNoInteractions(entitlements);
 }
 @Test void permitsTenantWideAuthorizedUser(){
  UUID tenant=UUID.randomUUID();
  when(access.hasTenantWidePermission(auth,tenant,"orders:read")).thenReturn(true);
  controller.snapshot(tenant,auth);
  verify(entitlements).snapshot(tenant);
 }
}
