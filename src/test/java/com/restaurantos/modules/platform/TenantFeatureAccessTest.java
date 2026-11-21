package com.restaurantos.modules.platform;
import com.restaurantos.modules.platform.repository.TenantFeatureAccessDao;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TenantFeatureAccessTest {
 @Test void blocksFeatureWhenSubscriptionGateDeniesIt(){
  TenantFeatureAccessDao dao=mock(TenantFeatureAccessDao.class);
  RestaurantFeatureAccessService gate=mock(RestaurantFeatureAccessService.class);
  UUID tenant=UUID.randomUUID();
  when(dao.enabled(tenant,"QR_MENU")).thenReturn(true);
  doThrow(new ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN))
   .when(gate).require(tenant,"QR_MENU");
  TenantFeatureAccess access=new TenantFeatureAccess(dao,gate);
  assertFalse(access.enabled(tenant,"QR_MENU"));
  assertThrows(ResponseStatusException.class,()->access.require(tenant,"QR_MENU"));
 }
 @Test void enforcesDependenciesAcrossPlanAndGrants(){
  TenantFeatureAccessDao dao=mock(TenantFeatureAccessDao.class);
  RestaurantFeatureAccessService gate=mock(RestaurantFeatureAccessService.class);
  UUID tenant=UUID.randomUUID();
  when(dao.enabled(tenant,"GUEST_ORDERING")).thenReturn(true);
  when(dao.enabled(tenant,"QR_MENU")).thenReturn(true);
  doThrow(new ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN))
   .when(gate).require(tenant,"QR_MENU");
  assertFalse(new TenantFeatureAccess(dao,gate).enabled(tenant,"GUEST_ORDERING"));
 }
 @Test void allowsFeatureWhenAllGatesAllow(){
  TenantFeatureAccessDao dao=mock(TenantFeatureAccessDao.class);
  RestaurantFeatureAccessService gate=mock(RestaurantFeatureAccessService.class);
  UUID tenant=UUID.randomUUID();
  when(dao.enabled(tenant,"QR_MENU")).thenReturn(true);
  assertTrue(new TenantFeatureAccess(dao,gate).enabled(tenant,"QR_MENU"));
  verify(gate).require(tenant,"QR_MENU");
 }
}
