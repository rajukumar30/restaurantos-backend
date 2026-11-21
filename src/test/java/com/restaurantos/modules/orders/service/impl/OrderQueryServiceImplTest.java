package com.restaurantos.modules.orders.service.impl;
import com.restaurantos.modules.orders.dto.OrderDto.*;
import com.restaurantos.modules.orders.repository.OrderQueryDao;
import com.restaurantos.modules.staff.TenantAccessService;
import com.restaurantos.modules.platform.RestaurantFeatureAccessService;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class OrderQueryServiceImplTest {
 private final OrderQueryDao dao=mock(OrderQueryDao.class);
 private final TenantAccessService access=mock(TenantAccessService.class);
 private final Authentication auth=mock(Authentication.class);
 private final RestaurantFeatureAccessService features=mock(RestaurantFeatureAccessService.class);
 private final OrderQueryServiceImpl service=new OrderQueryServiceImpl(dao,access,features);
 @Test void rejectsUnknownPublicOrder(){
  UUID id=UUID.randomUUID();
  when(dao.publicStatus("token",id)).thenReturn(List.of());
  assertThrows(ResponseStatusException.class,()->service.publicStatus("token",id));
 }
 @Test void rejectsUnsupportedTransitionBeforeWrite(){
  UUID tenant=UUID.randomUUID(),branch=UUID.randomUUID(),order=UUID.randomUUID();
  assertThrows(ResponseStatusException.class,()->service.updateStatus(tenant,order,new UpdateOrderStatusRequest(branch,"READY"),auth));
  verify(dao,never()).updateStatus(any(),any(),any(),any());
 }
 @Test void rejectsStaleTransition(){
  UUID tenant=UUID.randomUUID(),branch=UUID.randomUUID(),order=UUID.randomUUID();
  assertThrows(ResponseStatusException.class,()->service.updateStatus(tenant,order,new UpdateOrderStatusRequest(branch,"SERVED"),auth));
  verify(dao,never()).recordServed(any(),any(),any(),any());
 }
 @Test void deniesOrderListingWhenPlanDoesNotIncludeOrdering(){
  UUID tenant=UUID.randomUUID(),branch=UUID.randomUUID();
  doThrow(new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN))
   .when(features).require(tenant,"GUEST_ORDERING");
  assertThrows(ResponseStatusException.class,()->service.list(tenant,branch,auth));
  verify(dao,never()).list(any(),any());
 }
 @Test void scopesOrderListToTenantAndBranch(){
  UUID tenant=UUID.randomUUID(),branch=UUID.randomUUID();
  service.list(tenant,branch,auth);
  verify(access).requirePermission(auth,tenant,branch,"orders:read");
  verify(access).requireBranchInTenant(tenant,branch);
  verify(features).require(tenant,"GUEST_ORDERING");
  verify(dao).list(tenant,branch);
 }
}
