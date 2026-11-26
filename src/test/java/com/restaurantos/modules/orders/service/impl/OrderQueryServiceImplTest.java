package com.restaurantos.modules.orders.service.impl;
import com.restaurantos.modules.orders.dto.OrderDto.*;
import com.restaurantos.modules.orders.repository.OrderQueryDao;
import com.restaurantos.modules.staff.TenantAccessService;
import com.restaurantos.modules.platform.TenantFeatureAccess;
import com.restaurantos.modules.realtime.service.RealtimeEventPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class OrderQueryServiceImplTest {
 private final OrderQueryDao dao=mock(OrderQueryDao.class);
 private final TenantAccessService access=mock(TenantAccessService.class);
 private final Authentication auth=mock(Authentication.class);
 private final TenantFeatureAccess features=mock(TenantFeatureAccess.class);
 private final OrderQueryServiceImpl service=new OrderQueryServiceImpl(dao,access,features,mock(RealtimeEventPublisher.class));
 @Test void rejectsUnknownPublicOrder(){
  UUID id=UUID.randomUUID();
  when(dao.publicStatus("token",id)).thenReturn(List.of());
  assertThrows(ResponseStatusException.class,()->service.publicStatus("token",id));
 }
 @Test void rejectsUnsupportedTransitionBeforeWrite(){
  UUID tenant=UUID.randomUUID(),branch=UUID.randomUUID(),order=UUID.randomUUID();
  assertThrows(ResponseStatusException.class,()->service.updateStatus(tenant,order,new UpdateOrderStatusRequest(branch,"INVALID"),auth));
  verify(dao,never()).updateStatus(any(),any(),any(),any());
 }
 @Test void allowsKitchenTransitions(){
  UUID tenant=UUID.randomUUID(),branch=UUID.randomUUID(),order=UUID.randomUUID();
  for(String status:List.of("ACCEPTED","PREPARING","READY")){
   when(dao.updateStatus(tenant,branch,order,status)).thenReturn(1);
   assertEquals(status,service.updateStatus(tenant,order,new UpdateOrderStatusRequest(branch,status),auth).status());
   verify(dao).updateStatus(tenant,branch,order,status);
  }
  verify(dao,never()).recordServed(any(),any(),any(),any());
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
 @Test void liveQueueRetainsAllAuthorizationChecks(){
  UUID tenant=UUID.randomUUID(),branch=UUID.randomUUID();
  service.listLive(tenant,branch,auth);
  verify(access).requirePermission(auth,tenant,branch,"orders:read");
  verify(access).requireBranchInTenant(tenant,branch);
  verify(features).require(tenant,"GUEST_ORDERING");
  verify(dao).listLive(tenant,branch);
 }
 @Test void liveQueueDeniesDisabledFeature(){
  UUID tenant=UUID.randomUUID(),branch=UUID.randomUUID();
  doThrow(new ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN)).when(features).require(tenant,"GUEST_ORDERING");
  assertThrows(ResponseStatusException.class,()->service.listLive(tenant,branch,auth));
  verify(dao,never()).listLive(any(),any());
 }
 @Test void scopesOrderListToTenantAndBranch(){
  UUID tenant=UUID.randomUUID(),branch=UUID.randomUUID();
  service.list(tenant,branch,auth);
  verify(access).requirePermission(auth,tenant,branch,"orders:read");
  verify(access).requireBranchInTenant(tenant,branch);
  verify(features).require(tenant,"GUEST_ORDERING");
  verify(dao).list(tenant,branch);
 }
 @Test void workspaceDefaultsToTodayInBranchTimezone(){
  UUID tenant=UUID.randomUUID(),branch=UUID.randomUUID();
  ZoneId zone=ZoneId.of("Asia/Kolkata");
  when(dao.branchTimezone(tenant,branch)).thenReturn(zone.getId());
  var query=new OrderWorkspaceQuery(branch,null,"day","ALL","",1,20);
  LocalDate before=LocalDate.now(zone);
  service.workspace(tenant,query,auth);
  var captured=org.mockito.ArgumentCaptor.forClass(LocalDate.class);
  var today=org.mockito.ArgumentCaptor.forClass(LocalDate.class);
  verify(dao).workspace(eq(tenant),eq(query),captured.capture(),today.capture(),eq(zone));
  assertEquals(today.getValue(),captured.getValue());
  assertTrue(captured.getValue().equals(before)||captured.getValue().equals(LocalDate.now(zone)));
  verify(access).requirePermission(auth,tenant,branch,"orders:read");
  verify(access).requireBranchInTenant(tenant,branch);
  verify(features).require(tenant,"GUEST_ORDERING");
  verify(dao,never()).list(any(),any());
 }
 @Test void workspaceUsesRequestedDateAndBranchZone(){
  UUID tenant=UUID.randomUUID(),branch=UUID.randomUUID();ZoneId zone=ZoneId.of("America/New_York");
  when(dao.branchTimezone(tenant,branch)).thenReturn(zone.getId());
  LocalDate date=LocalDate.of(2024,3,10);
  var query=new OrderWorkspaceQuery(branch,date,"day","READY","table",2,20);
  service.workspace(tenant,query,auth);
  verify(dao).workspace(eq(tenant),eq(query),eq(date),any(LocalDate.class),eq(zone));
 }
 @Test void workspaceRejectsOversizedPageAndUnsupportedMode(){
  UUID tenant=UUID.randomUUID(),branch=UUID.randomUUID();
  assertThrows(ResponseStatusException.class,()->service.workspace(tenant,new OrderWorkspaceQuery(branch,null,"day","ALL","",1,1000),auth));
  assertThrows(ResponseStatusException.class,()->service.workspace(tenant,new OrderWorkspaceQuery(branch,null,"all-time","ALL","",1,20),auth));
  verify(dao,never()).workspace(any(),any(),any(),any(),any());
 }
 @Test void workspaceRejectsFutureDates(){
  UUID tenant=UUID.randomUUID(),branch=UUID.randomUUID();
  when(dao.branchTimezone(tenant,branch)).thenReturn("Asia/Kolkata");
  assertThrows(ResponseStatusException.class,()->service.workspace(tenant,new OrderWorkspaceQuery(branch,LocalDate.now(ZoneId.of("Asia/Kolkata")).plusDays(2),"day","ALL","",1,20),auth));
  verify(dao,never()).workspace(any(),any(),any(),any(),any());
 }
 @Test void workspaceDeniedWhenOrderingDisabled(){
  UUID tenant=UUID.randomUUID(),branch=UUID.randomUUID();
  doThrow(new ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN)).when(features).require(tenant,"GUEST_ORDERING");
  assertThrows(ResponseStatusException.class,()->service.workspace(tenant,new OrderWorkspaceQuery(branch,null,"live","ALL","",1,20),auth));
  verifyNoInteractions(dao);
 }
 @Test void historyWithoutDateIncludesAllDates(){
  UUID tenant=UUID.randomUUID(),branch=UUID.randomUUID();
  ZoneId zone=ZoneId.of("Asia/Kolkata");
  when(dao.branchTimezone(tenant,branch)).thenReturn(zone.getId());
  var query=new OrderWorkspaceQuery(branch,null,"history","ALL","",1,20);
  service.workspace(tenant,query,auth);
  verify(dao).workspace(eq(tenant),eq(query),isNull(),any(LocalDate.class),eq(zone));
  verify(access).requirePermission(auth,tenant,branch,"orders:read");
 }
 @Test void historyCanBeRestrictedToSpecificDate(){
  UUID tenant=UUID.randomUUID(),branch=UUID.randomUUID();
  ZoneId zone=ZoneId.of("Asia/Kolkata");
  when(dao.branchTimezone(tenant,branch)).thenReturn(zone.getId());
  LocalDate date=LocalDate.of(2025,1,2);
  var query=new OrderWorkspaceQuery(branch,date,"history","SERVED","rice",2,20);
  service.workspace(tenant,query,auth);
  verify(dao).workspace(eq(tenant),eq(query),eq(date),any(LocalDate.class),eq(zone));
 }
}
