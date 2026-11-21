package com.restaurantos.modules.orders.service.impl;
import com.restaurantos.modules.orders.dto.GuestCustomerDto.*;
import com.restaurantos.modules.orders.repository.GuestCustomerHistoryDao;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class GuestCustomerHistoryServiceImplTest {
 private final GuestCustomerHistoryDao dao=mock(GuestCustomerHistoryDao.class);
 @Test void demoOtpDisabledDoesNotWrite(){
  GuestCustomerHistoryServiceImpl service=new GuestCustomerHistoryServiceImpl(dao,false);
  assertThrows(ResponseStatusException.class,()->service.request("qr",new PhoneRequest("+919876543210")));
  verifyNoInteractions(dao);
 }
 @Test void invalidQrDoesNotCreateChallenge(){
  GuestCustomerHistoryServiceImpl service=new GuestCustomerHistoryServiceImpl(dao,true);
  assertThrows(ResponseStatusException.class,()->service.request("bad",new PhoneRequest("+919876543210")));
  verify(dao,never()).createChallenge(any(),any(),any());
 }
 @Test void rejectsInvalidSessionBeforeLookup(){
  UUID tenant=UUID.randomUUID();
  when(dao.tenant("qr")).thenReturn(List.of(tenant));
  GuestCustomerHistoryServiceImpl service=new GuestCustomerHistoryServiceImpl(dao,false);
  assertThrows(ResponseStatusException.class,()->service.orders("qr","Bearer invalid"));
  verify(dao,never()).orders(any(),any());
 }
 @Test void rateLimitsChallengeRequests(){
  UUID tenant=UUID.randomUUID();
  when(dao.tenant("qr")).thenReturn(List.of(tenant));
  when(dao.recentChallenges(tenant,"+919876543210")).thenReturn(3);
  GuestCustomerHistoryServiceImpl service=new GuestCustomerHistoryServiceImpl(dao,true);
  assertThrows(ResponseStatusException.class,()->service.request("qr",new PhoneRequest("+919876543210")));
  verify(dao,never()).createChallenge(any(),any(),any());
 }
}
