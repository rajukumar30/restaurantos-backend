package com.restaurantos.modules.service.service.impl;
import com.restaurantos.modules.service.dto.ServiceRequestDto.*;
import com.restaurantos.modules.service.repository.ServiceRequestDao;
import com.restaurantos.modules.staff.TenantAccessService;
import com.restaurantos.modules.platform.TenantFeatureAccess;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class ServiceRequestServiceImplTest {
 private final ServiceRequestDao dao=mock(ServiceRequestDao.class);
 private final TenantAccessService access=mock(TenantAccessService.class);
 private final TenantFeatureAccess features=mock(TenantFeatureAccess.class);
 private final ServiceRequestServiceImpl service=new ServiceRequestServiceImpl(dao,access,features);
 @Test void deniesGuestServiceRequestBeforeAnyWriteWhenFeatureIsDisabled(){
  UUID table=UUID.randomUUID(),tenant=UUID.randomUUID(),branch=UUID.randomUUID();
  when(dao.tables("qr")).thenReturn(List.of(new TableInfo(table,tenant,branch)));
  doThrow(new ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN))
   .when(features).require(tenant,"SERVICE_REQUESTS");
  assertThrows(ResponseStatusException.class,()->service.create("qr",new CreateRequest("WATER",null)));
  verify(dao,never()).lockTable(any());
  verify(dao,never()).insert(any(),any(),any());
 }
 @Test void deniesStaffServiceRequestsBeforeReadingTenantData(){
  UUID tenant=UUID.randomUUID(),branch=UUID.randomUUID();
  org.springframework.security.core.Authentication auth=mock(org.springframework.security.core.Authentication.class);
  doThrow(new ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN))
   .when(features).require(tenant,"SERVICE_REQUESTS");
  assertThrows(ResponseStatusException.class,()->service.staffList(tenant,branch,auth));
  verify(access).requirePermission(auth,tenant,branch,"orders:read");
  verify(dao,never()).staffList(any(),any());
 }
 @Test void rejectsInvalidQr(){
  assertThrows(ResponseStatusException.class,()->service.create("bad",new CreateRequest("WATER",null)));
  verify(dao,never()).insert(any(),any(),any());
 }
 @Test void rejectsUnsupportedRequestType(){
  UUID table=UUID.randomUUID(),tenant=UUID.randomUUID(),branch=UUID.randomUUID();
  when(dao.tables("qr")).thenReturn(List.of(new TableInfo(table,tenant,branch)));
  assertThrows(ResponseStatusException.class,()->service.create("qr",new CreateRequest("UNKNOWN",null)));
  verify(dao,never()).lockTable(any());
 }
 @Test void rejectsOtherWithoutNotes(){
  UUID table=UUID.randomUUID(),tenant=UUID.randomUUID(),branch=UUID.randomUUID();
  when(dao.tables("qr")).thenReturn(List.of(new TableInfo(table,tenant,branch)));
  assertThrows(ResponseStatusException.class,()->service.create("qr",new CreateRequest("OTHER",null)));
 }
}
