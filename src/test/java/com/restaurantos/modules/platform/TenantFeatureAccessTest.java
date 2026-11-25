package com.restaurantos.modules.platform;

import com.restaurantos.modules.platform.repository.TenantFeatureAccessDao;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.util.Set;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TenantFeatureAccessTest {
 private final TenantFeatureAccessDao dao=mock(TenantFeatureAccessDao.class);
 private final RestaurantFeatureAccessService gate=mock(RestaurantFeatureAccessService.class);
 private final TenantFeatureAccess access=new TenantFeatureAccess(dao,gate);
 private final UUID tenant=UUID.randomUUID();

 @Test void deniesWhenEntitlementMissing(){
  when(dao.allEntitled(tenant,Set.of("QR_MENU"))).thenReturn(false);
  assertFalse(access.enabled(tenant,"QR_MENU"));
  assertThrows(ResponseStatusException.class,()->access.require(tenant,"QR_MENU"));
 }
 @Test void enforcesTransitiveDependenciesInSingleQuery(){
  Set<String> keys=Set.of("KITCHEN_DISPLAY","GUEST_ORDERING","QR_MENU");
  when(dao.allEntitled(tenant,keys)).thenReturn(false);
  assertFalse(access.enabled(tenant,"KITCHEN_DISPLAY"));
  verify(dao).allEntitled(tenant,keys);
  verifyNoMoreInteractions(dao);
 }
 @Test void allowsFeatureWhenAllEntitlementsAllow(){
  when(dao.allEntitled(tenant,Set.of("GUEST_ORDERING","QR_MENU"))).thenReturn(true);
  assertDoesNotThrow(()->access.require(tenant,"GUEST_ORDERING"));
  verify(dao).allEntitled(tenant,Set.of("GUEST_ORDERING","QR_MENU"));
  verifyNoMoreInteractions(dao);
 }
 @Test void nullInputFailsClosed(){
  assertFalse(access.enabled(null,"QR_MENU"));
  assertFalse(access.enabled(tenant,null));
  verifyNoInteractions(dao);
 }
}
