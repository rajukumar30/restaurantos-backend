package com.restaurantos.modules.orders.service.impl;
import com.restaurantos.modules.orders.dto.OrderDto.*;
import com.restaurantos.modules.orders.entity.*;
import com.restaurantos.modules.orders.repository.OrderPlacementDao;
import com.restaurantos.modules.platform.TenantFeatureAccess;
import com.restaurantos.modules.realtime.service.RealtimeEventPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class OrderPlacementServiceImplTest {
 private final OrderPlacementDao dao=mock(OrderPlacementDao.class);
 private final TenantFeatureAccess feature=mock(TenantFeatureAccess.class);
 private final OrderPlacementServiceImpl service=new OrderPlacementServiceImpl(dao,feature,mock(RealtimeEventPublisher.class));
 private final UUID tenant=UUID.randomUUID(),branch=UUID.randomUUID(),tableId=UUID.randomUUID(),item=UUID.randomUUID();
 private PlaceOrderRequest request(List<OrderLineRequest> items){
  return new PlaceOrderRequest(items,"Customer","+919876543210",null);
 }
 @Test void rejectsInvalidQrBeforeAnyWrites(){
  when(dao.findActiveTable("invalid")).thenReturn(List.of());
  assertThrows(ResponseStatusException.class,()->service.place("invalid",request(List.of(new OrderLineRequest(item,1))),null));
  verify(dao,never()).createOrder(any(),any(),any(),any(),any(),any(),any(),any(),any(),any());
 }
 @Test void deniesOrderPlacementWhenQrMenuDependencyIsUnavailable(){
  when(dao.findActiveTable("qr")).thenReturn(List.of(new QrTable(tableId,tenant,branch,"Table 1")));
  doThrow(new ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN))
   .when(feature).require(tenant,"GUEST_ORDERING");
  assertThrows(ResponseStatusException.class,()->service.place("qr",request(List.of(new OrderLineRequest(item,1))),null));
  verify(dao,never()).lockTable(any());
  verify(dao,never()).createOrder(any(),any(),any(),any(),any(),any(),any(),any(),any(),any());
 }
 @Test void rejectsCombinedQuantityOverLimit(){
  when(dao.findActiveTable("qr")).thenReturn(List.of(new QrTable(tableId,tenant,branch,"Table 1")));
  assertThrows(ResponseStatusException.class,()->service.place("qr",request(List.of(new OrderLineRequest(item,15),new OrderLineRequest(item,6))),null));
  verify(dao,never()).lockTable(any());
 }
 @Test void rejectsUnavailableItem(){
  when(dao.findActiveTable("qr")).thenReturn(List.of(new QrTable(tableId,tenant,branch,"Table 1")));
  assertThrows(ResponseStatusException.class,()->service.place("qr",request(List.of(new OrderLineRequest(item,1))),null));
  verify(dao,never()).lockTable(any());
 }
 @Test void duplicateSubmissionReturnsOriginalReceipt(){
  QrTable table=new QrTable(tableId,tenant,branch,"Table 1");
  UUID original=UUID.randomUUID();
  when(dao.findActiveTable("qr")).thenReturn(List.of(table));
  when(dao.findAvailableItem(tenant,branch,item,2)).thenReturn(List.of(new ValidatedOrderLine(item,"Tea",new BigDecimal("30"),2)));
  when(dao.findSubmission(eq(table),eq("fixed-idempotency-key-1234"))).thenAnswer(invocation->{
   // The hash is stored with the original request; compare on the same canonical payload.
   return List.of();
  });
  assertThrows(ResponseStatusException.class,()->service.place("qr",request(List.of(new OrderLineRequest(item,2))),"short"));
  verify(dao,never()).createOrder(any(),any(),any(),any(),any(),any(),any(),any(),any(),any());
 }
 @Test void successfulOrderCreatesVisitAndLine(){
  QrTable table=new QrTable(tableId,tenant,branch,"Table 1");
  when(dao.findActiveTable("qr")).thenReturn(List.of(table));
  when(dao.findAvailableItem(tenant,branch,item,2)).thenReturn(List.of(new ValidatedOrderLine(item,"Tea",new BigDecimal("30"),2)));
  OrderReceipt receipt=service.place("qr",request(List.of(new OrderLineRequest(item,2))),null);
  assertEquals(new BigDecimal("60"),receipt.totalAmount());
  assertEquals("NEW",receipt.status());
  verify(feature).require(tenant,"GUEST_ORDERING");
  verify(dao).lockTable(tableId);
  verify(dao).createVisit(any(),eq(table),any());
  verify(dao).createOrderItem(eq(tenant),eq(receipt.orderId()),any());
 }
}
