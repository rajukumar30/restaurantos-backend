package com.restaurantos.modules.platform.service.impl;

import com.restaurantos.modules.platform.repository.PlatformEntitlementsDao;
import com.restaurantos.modules.platform.repository.PlatformEntitlementsDao.FeatureFlags;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlatformEntitlementsServiceImplTest {
 private final PlatformEntitlementsDao dao=mock(PlatformEntitlementsDao.class);
 private final PlatformEntitlementsServiceImpl service=new PlatformEntitlementsServiceImpl(dao,7);
 private final UUID tenant=UUID.randomUUID();
 private void features(){
  when(dao.tenantExists(tenant)).thenReturn(true);
  when(dao.features(eq(tenant),any())).thenReturn(List.of(
   new FeatureFlags("QR_MENU","QR Menu","GUEST",true,true,true,false),
   new FeatureFlags("GUEST_ORDERING","Guest ordering","GUEST",true,true,true,false),
   new FeatureFlags("KITCHEN_DISPLAY","Kitchen","STAFF",true,true,true,false)));
 }
 private void subscription(LocalDate start,LocalDate end){
  UUID plan=UUID.randomUUID();
  when(dao.subscriptions(tenant)).thenReturn(List.of(Map.of(
   "plan_id",plan,"plan_name","Basic","starts_on",start,"ends_on",end)));
 }
 @Test void deniesAllPlanFeaturesWithoutSubscription(){
  features();
  when(dao.subscriptions(tenant)).thenReturn(List.of());
  var result=service.snapshot(tenant);
  assertEquals("UNASSIGNED",result.subscriptionStatus());
  assertFalse(result.accessEligible());
  assertTrue(result.features().stream().noneMatch(f->f.effectiveEnabled()));
 }
 @Test void activePlanEnablesFeaturesWithSatisfiedDependencies(){
  features();
  LocalDate today=LocalDate.now(ZoneOffset.UTC);
  subscription(today.minusDays(1),today.plusDays(30));
  var result=service.snapshot(tenant);
  assertEquals("ACTIVE",result.subscriptionStatus());
  assertTrue(result.features().stream().allMatch(f->f.effectiveEnabled()));
 }
 @Test void expiredPlanDisablesAllFeatures(){
  features();
  LocalDate today=LocalDate.now(ZoneOffset.UTC);
  subscription(today.minusDays(30),today.minusDays(10));
  var result=service.snapshot(tenant);
  assertEquals("EXPIRED",result.subscriptionStatus());
  assertTrue(result.features().stream().noneMatch(f->f.effectiveEnabled()));
 }
 @Test void disabledPrerequisiteDisablesDependentFeature(){
  when(dao.tenantExists(tenant)).thenReturn(true);
  LocalDate today=LocalDate.now(ZoneOffset.UTC);
  subscription(today.minusDays(1),today.plusDays(30));
  when(dao.features(eq(tenant),any())).thenReturn(List.of(
   new FeatureFlags("QR_MENU","QR Menu","GUEST",true,false,true,false),
   new FeatureFlags("GUEST_ORDERING","Guest ordering","GUEST",true,true,true,false)));
  var result=service.snapshot(tenant);
  assertFalse(result.features().stream().filter(f->f.key().equals("GUEST_ORDERING")).findFirst().orElseThrow().effectiveEnabled());
 }
}
