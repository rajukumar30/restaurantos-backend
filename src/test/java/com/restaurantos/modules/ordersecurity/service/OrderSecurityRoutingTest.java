package com.restaurantos.modules.ordersecurity.service;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurantos.modules.ordersecurity.dto.OrderSecurityDto.*;
import com.restaurantos.modules.ordersecurity.repository.OrderSecurityDao;
import com.restaurantos.modules.dining.dto.DiningDto.Visit;
import com.restaurantos.modules.dining.repository.DiningDao;
import com.restaurantos.modules.dining.service.DiningSessionService;
import com.restaurantos.modules.orders.dto.CheckoutDto.Scope;
import com.restaurantos.modules.orders.dto.OrderDto.*;
import com.restaurantos.modules.orders.entity.*;
import com.restaurantos.modules.orders.repository.OrderPlacementDao;
import com.restaurantos.modules.orders.service.OrderPlacementService;
import com.restaurantos.modules.platform.TenantFeatureAccess;
import com.restaurantos.modules.staff.TenantAccessService;
import com.restaurantos.modules.realtime.service.RealtimeEventPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class OrderSecurityRoutingTest {
 private final OrderSecurityDao dao=mock(OrderSecurityDao.class);
 private final DiningDao sessions=mock(DiningDao.class);
 private final DiningSessionService dining=mock(DiningSessionService.class);
 private final OrderPlacementDao orders=mock(OrderPlacementDao.class);
 private final OrderPlacementService placement=mock(OrderPlacementService.class);
 private final TenantAccessService access=mock(TenantAccessService.class);
 private final OrderSecurityService service=new OrderSecurityService(dao,sessions,dining,orders,placement,access,mock(TenantFeatureAccess.class),mock(RealtimeEventPublisher.class),new ObjectMapper());
 private final Scope scope=new Scope(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID());
 private final UUID session=UUID.randomUUID(),item=UUID.randomUUID();
 private final PlaceOrderRequest request=new PlaceOrderRequest(List.of(new OrderLineRequest(item,1)),"Guest","+919876543210",null);
 private final String key="a".repeat(64);
 private void setup(String mode,boolean verified){
  when(dining.requireOrdering("qr","token",true,true)).thenReturn(new Visit(scope,session,"OPEN",verified));
  when(dining.orderKey(any(),eq(key))).thenReturn(key);
  when(dao.settings(scope.tenant(),scope.branch())).thenReturn(new Settings(mode,new BigDecimal("1000"),new BigDecimal("2000"),6));
  when(dao.replay(scope,key)).thenReturn(Optional.empty());
  when(dao.unverifiedTotal(scope.visit())).thenReturn(BigDecimal.ZERO);
  when(orders.findAvailableItems(eq(scope.tenant()),eq(scope.branch()),anyMap())).thenReturn(List.of(new ValidatedOrderLine(item,"Rice",new BigDecimal("100"),1)));
  when(orders.findActiveTable("qr")).thenReturn(List.of(new QrTable(scope.table(),scope.tenant(),scope.branch(),"Table 1")));
 }
 @Test void unverifiedSessionCannotOrderInSecureMode(){setup("SECURE",false);assertThrows(ResponseStatusException.class,()->service.submit("qr","token",request,key));verifyNoInteractions(placement);}
 @Test void strictOrderIsHeldAndNeverPlaced(){setup("STRICT",true);var receipt=service.submit("qr","token",request,key);assertEquals("AWAITING_REVIEW",receipt.status());verifyNoInteractions(placement);verify(dao).insert(any(),eq(receipt.orderId()),eq(receipt.orderNumber()),eq(key),anyString(),anyString(),eq(new BigDecimal("100")),anyString(),anyString(),any());}
 @Test void expressLowValueOrderIsScopedAndMarkedUnverified(){setup("EXPRESS",false);UUID order=UUID.randomUUID();when(placement.placeChecked("qr",request,key,scope.visit(),null,new BigDecimal("100"))).thenReturn(new OrderReceipt(order,"R1","Table 1",new BigDecimal("100"),"NEW"));assertEquals("NEW",service.submit("qr","token",request,key).status());verify(dao).markUnverified(new Visit(scope,session,"OPEN",false),order);}
 @Test void expressCumulativeCapHoldsOrderOutsideBill(){setup("EXPRESS",false);when(dao.unverifiedTotal(scope.visit())).thenReturn(new BigDecimal("1950"));assertEquals("AWAITING_REVIEW",service.submit("qr","token",request,key).status());verifyNoInteractions(placement);}
}
