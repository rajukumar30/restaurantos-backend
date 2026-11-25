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
 @Test void duplicateSubmissionReturnsOriginalReceiptWithoutNewOrder(){
  QrTable table=new QrTable(tableId,tenant,branch,"Table 1");
  UUID original=UUID.randomUUID();
  String key="fixed-idempotency-key-1234";
  when(dao.findActiveTable("qr")).thenReturn(List.of(table));
  when(dao.findAvailableItems(tenant,branch,Map.of(item,2))).thenReturn(List.of(new ValidatedOrderLine(item,"Tea",new BigDecimal("30"),2)));
  String canonical="Customer|+919876543210|"+ "|" +item+":2";
  String hash;
  try{
   hash=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
     .digest(canonical.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
  }catch(java.security.NoSuchAlgorithmException ex){throw new AssertionError(ex);}
  when(dao.findSubmission(table,key)).thenReturn(List.of(Map.of(
    "orderId",original,"orderNumber","R123","totalAmount",new BigDecimal("60"),
    "status","NEW","requestHash",hash)));
  OrderReceipt receipt=service.place("qr",request(List.of(new OrderLineRequest(item,2))),key);
  assertEquals(original,receipt.orderId());
  assertEquals("R123",receipt.orderNumber());
  verify(dao,never()).createOrder(any(),any(),any(),any(),any(),any(),any(),any(),any(),any());
  verify(dao,never()).createOrderItems(any(),any(),any());
 }
 @Test void multiItemCartUsesOneValidationAndOneBatchInsert(){
  QrTable table=new QrTable(tableId,tenant,branch,"Table 1");
  UUID other=UUID.randomUUID();
  var lines=List.of(new ValidatedOrderLine(item,"Tea",new BigDecimal("30"),2),
    new ValidatedOrderLine(other,"Rice",new BigDecimal("50"),1));
  when(dao.findActiveTable("qr")).thenReturn(List.of(table));
  when(dao.findAvailableItems(tenant,branch,Map.of(item,2,other,1))).thenReturn(lines);
  var receipt=service.place("qr",request(List.of(new OrderLineRequest(item,1),new OrderLineRequest(other,1),new OrderLineRequest(item,1))),null);
  assertEquals(new BigDecimal("110"),receipt.totalAmount());
  verify(dao).findAvailableItems(tenant,branch,Map.of(item,2,other,1));
  verify(dao).createOrderItems(tenant,receipt.orderId(),lines);
 }
 @Test void rejectsInvalidSubmissionKey(){
  assertThrows(ResponseStatusException.class,()->service.place("qr",request(List.of(new OrderLineRequest(item,1))),"short"));
  verifyNoInteractions(dao);
 }
 @Test void successfulOrderCreatesVisitAndLine(){
  QrTable table=new QrTable(tableId,tenant,branch,"Table 1");
  when(dao.findActiveTable("qr")).thenReturn(List.of(table));
  when(dao.findAvailableItems(tenant,branch,Map.of(item,2))).thenReturn(List.of(new ValidatedOrderLine(item,"Tea",new BigDecimal("30"),2)));
  OrderReceipt receipt=service.place("qr",request(List.of(new OrderLineRequest(item,2))),null);
  assertEquals(new BigDecimal("60"),receipt.totalAmount());
  assertEquals("NEW",receipt.status());
  verify(feature).require(tenant,"GUEST_ORDERING");
  verify(dao).lockTable(tableId);
  verify(dao).createVisit(any(),eq(table),any());
  verify(dao).createOrderItems(eq(tenant),eq(receipt.orderId()),any());
 }
}
