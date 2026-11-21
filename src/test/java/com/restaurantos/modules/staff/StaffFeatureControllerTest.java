package com.restaurantos.modules.staff;

import com.restaurantos.modules.auth.repository.AuthDao;
import com.restaurantos.modules.platform.TenantFeatureAccess;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StaffFeatureControllerTest {
 private final AuthDao auth=mock(AuthDao.class);
 private final TenantFeatureAccess features=mock(TenantFeatureAccess.class);
 private final StaffFeatureController controller=new StaffFeatureController(auth,features);
 private final Authentication authentication=mock(Authentication.class);
 @Test void deniesUsersWithoutTenantMembership(){
  UUID user=UUID.randomUUID(),tenant=UUID.randomUUID();
  when(authentication.getPrincipal()).thenReturn(user);
  assertThrows(ResponseStatusException.class,()->controller.available(tenant,authentication));
  verifyNoInteractions(features);
 }
 @Test void onlyReturnsEnabledFeaturesForAuthorizedStaff(){
  UUID user=UUID.randomUUID(),tenant=UUID.randomUUID();
  when(authentication.getPrincipal()).thenReturn(user);
  when(auth.roleKeys(user,tenant)).thenReturn(List.of("WAITER"));
  when(features.enabled(tenant,"WAITER_DASHBOARD")).thenReturn(true);
  var result=controller.available(tenant,authentication);
  assertNotNull(result);
  verify(features).enabled(tenant,"WAITER_DASHBOARD");
  verify(features).enabled(tenant,"KITCHEN_DISPLAY");
 }
}
