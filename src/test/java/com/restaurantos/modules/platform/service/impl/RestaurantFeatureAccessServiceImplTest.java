package com.restaurantos.modules.platform.service.impl;

import com.restaurantos.modules.platform.dto.PlatformEntitlementsDto.*;
import com.restaurantos.modules.platform.service.PlatformEntitlementsService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RestaurantFeatureAccessServiceImplTest {
 private final PlatformEntitlementsService entitlements=mock(PlatformEntitlementsService.class);
 private final RestaurantFeatureAccessServiceImpl guard=new RestaurantFeatureAccessServiceImpl(entitlements);
 private final UUID tenant=UUID.randomUUID();
 private Snapshot snapshot(boolean eligible,boolean featureEnabled){
  return new Snapshot(tenant,UUID.randomUUID(),"Basic",eligible?"ACTIVE":"EXPIRED",eligible,
   List.of(new Entitlement("KITCHEN_DISPLAY","Kitchen","STAFF",true,true,true,featureEnabled,
    featureEnabled?"ENABLED":"NOT_IN_PLAN")));
 }
 @Test void allowsIncludedFeatureForEligibleSubscription(){
  when(entitlements.snapshot(tenant)).thenReturn(snapshot(true,true));
  assertTrue(guard.hasFeature(tenant,"KITCHEN_DISPLAY"));
  assertDoesNotThrow(()->guard.requireFeature(tenant,"KITCHEN_DISPLAY"));
 }
 @Test void deniesExpiredSubscriptionEvenWhenFeatureFlagIsEnabled(){
  when(entitlements.snapshot(tenant)).thenReturn(snapshot(false,true));
  assertEquals(HttpStatus.FORBIDDEN,assertThrows(ResponseStatusException.class,
   ()->guard.requireFeature(tenant,"KITCHEN_DISPLAY")).getStatusCode());
 }
 @Test void deniesFeatureNotIncludedInPlan(){
  when(entitlements.snapshot(tenant)).thenReturn(snapshot(true,false));
  assertFalse(guard.hasFeature(tenant,"KITCHEN_DISPLAY"));
 }
 @Test void rejectsInvalidFeatureKeyWithoutDatabaseLookup(){
  assertFalse(guard.hasFeature(tenant,"../KITCHEN_DISPLAY"));
  verifyNoInteractions(entitlements);
 }
}
