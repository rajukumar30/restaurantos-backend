package com.restaurantos.modules.staff.service.impl;
import com.restaurantos.modules.staff.TenantAccessService;
import com.restaurantos.modules.platform.TenantFeatureAccess;
import com.restaurantos.modules.staff.dto.StaffOperationsDto.KitchenStatusRequest;
import com.restaurantos.modules.staff.repository.StaffOperationsDao;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class StaffOperationsServiceImplTest {
 private final StaffOperationsDao dao=mock(StaffOperationsDao.class);
 private final TenantAccessService access=mock(TenantAccessService.class);
 private final Authentication auth=mock(Authentication.class);
 private final TenantFeatureAccess features=mock(TenantFeatureAccess.class);
 private final StaffOperationsServiceImpl service=new StaffOperationsServiceImpl(dao,access,features);
 @Test void disabledKitchenFeaturePreventsOrderStatusMutation(){
  UUID tenant=UUID.randomUUID(),branch=UUID.randomUUID(),order=UUID.randomUUID();
  doThrow(new ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN))
   .when(features).require(tenant,"KITCHEN_DISPLAY");
  assertThrows(ResponseStatusException.class,()->service.kitchenStatus(tenant,order,new KitchenStatusRequest(branch,"ACCEPTED"),auth));
  verify(access).requirePermission(auth,tenant,branch,"kitchen:operate");
  verify(dao,never()).updateStatus(any(),any(),any(),any(),any());
 }
 @Test void disabledKitchenFeaturePreventsHandoff(){
  UUID tenant=UUID.randomUUID(),branch=UUID.randomUUID(),order=UUID.randomUUID();
  doThrow(new ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN))
   .when(features).require(tenant,"KITCHEN_DISPLAY");
  assertThrows(ResponseStatusException.class,()->service.kitchenServed(tenant,order,new KitchenStatusRequest(branch,"SERVED"),auth));
  verify(dao,never()).updateStatus(any(),any(),any(),any(),any());
  verify(dao,never()).recordKitchenHandoff(any(),any(),any(),any());
 }
 @Test void rejectsInvalidKitchenTransition(){
  UUID tenant=UUID.randomUUID(),branch=UUID.randomUUID(),order=UUID.randomUUID();
  assertThrows(ResponseStatusException.class,()->service.kitchenStatus(tenant,order,new KitchenStatusRequest(branch,"SERVED"),auth));
  verify(dao,never()).updateStatus(any(),any(),any(),any(),any());
 }
 @Test void requiresKitchenManagedModeForHandoff(){
  UUID tenant=UUID.randomUUID(),branch=UUID.randomUUID(),order=UUID.randomUUID();
  when(dao.mode(tenant,branch)).thenReturn("WAITER_CONFIRMED");
  assertThrows(ResponseStatusException.class,()->service.kitchenServed(tenant,order,new KitchenStatusRequest(branch,"SERVED"),auth));
  verify(dao,never()).recordKitchenHandoff(any(),any(),any(),any());
 }
 @Test void acceptsExpectedKitchenTransition(){
  UUID tenant=UUID.randomUUID(),branch=UUID.randomUUID(),order=UUID.randomUUID();
  when(dao.updateStatus(tenant,branch,order,"ACCEPTED","NEW")).thenReturn(1);
  assertEquals("ACCEPTED",service.kitchenStatus(tenant,order,new KitchenStatusRequest(branch,"ACCEPTED"),auth).get("status"));
  verify(access).requirePermission(auth,tenant,branch,"kitchen:operate");
  verify(features).require(tenant,"KITCHEN_DISPLAY");
 }
}
