package com.restaurantos.modules.orders.service.impl;
import com.restaurantos.modules.orders.dto.CheckoutDto.*;
import com.restaurantos.modules.orders.repository.CheckoutDao;
import com.restaurantos.modules.platform.TenantFeatureAccess;
import com.restaurantos.modules.realtime.service.RealtimeEventPublisher;
import com.restaurantos.modules.staff.TenantAccessService;
import com.restaurantos.modules.staff.repository.StaffBillingDao;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class CheckoutServiceImplTest {
 private final CheckoutDao dao=mock(CheckoutDao.class);private final StaffBillingDao billing=mock(StaffBillingDao.class);
 private final TenantAccessService access=mock(TenantAccessService.class);private final TenantFeatureAccess features=mock(TenantFeatureAccess.class);
 private final RealtimeEventPublisher events=mock(RealtimeEventPublisher.class);private final Authentication auth=mock(Authentication.class);
 private final CheckoutServiceImpl service=new CheckoutServiceImpl(dao,billing,access,features,events);
 private final UUID tenant=UUID.randomUUID(),branch=UUID.randomUUID(),table=UUID.randomUUID(),visit=UUID.randomUUID(),actor=UUID.randomUUID(),key=UUID.randomUUID();
 private final Scope scope=new Scope(tenant,branch,table,visit);
 private Bill bill(String status,int unfinished){return new Bill(visit,table,"Table 1","B1",status,Instant.now(),new BigDecimal("1284.00"),new BigDecimal("100.00"),new BigDecimal("1184.00"),3,unfinished,"merchant@bank","Restaurant",Instant.now());}
 private void authorized(Bill b){when(access.requirePermission(auth,tenant,branch,"payments:read")).thenReturn(actor);when(billing.cashier(tenant,branch,actor)).thenReturn(true);when(dao.scope(tenant,branch,visit)).thenReturn(Optional.of(scope));when(dao.bill(scope)).thenReturn(b);when(dao.frozen(scope)).thenReturn(b.due());}
 private Confirm payment(String method,String ref,BigDecimal amount){return new Confirm(branch,key,amount,method,ref);}
 @Test void onePaymentAllocatesAllOutstandingOrdersAndSettlesOnce(){authorized(bill("PAYMENT_PENDING",0));var a=new Allocation(UUID.randomUUID(),new BigDecimal("400.00"));var b=new Allocation(UUID.randomUUID(),new BigDecimal("598.00"));var c=new Allocation(UUID.randomUUID(),new BigDecimal("186.00"));when(dao.allocations(scope)).thenReturn(List.of(a,b,c));service.confirm(tenant,visit,payment("UPI","TXN123",new BigDecimal("1184.00")),auth);verify(dao).record(scope,key,"UPI","TXN123",new BigDecimal("1184.00"));verify(dao).allocate(scope,a,"UPI","TXN123",actor);verify(dao).allocate(scope,b,"UPI",null,actor);verify(dao).allocate(scope,c,"UPI",null,actor);verify(dao).settle(scope);verify(events).publishBillingAfterCommit(any());}
 @Test void rejectsStaleAmountAndUnverifiedUpi(){authorized(bill("PAYMENT_PENDING",0));assertThrows(ResponseStatusException.class,()->service.confirm(tenant,visit,payment("UPI",null,new BigDecimal("1184.00")),auth));assertThrows(ResponseStatusException.class,()->service.confirm(tenant,visit,payment("CASH",null,new BigDecimal("1284.00")),auth));verify(dao,never()).record(any(),any(),any(),any(),any());}
 @Test void duplicateKeyReturnsExistingBillWithoutAnotherCharge(){authorized(bill("PAID",0));when(dao.replay(scope,key)).thenReturn(Optional.of(Map.of("visit_id",visit,"provider","MANUAL_UPI","provider_order_id","TXN123","amount",new BigDecimal("1184.00"))));service.confirm(tenant,visit,payment("UPI","TXN123",new BigDecimal("1184.00")),auth);verify(dao,never()).record(any(),any(),any(),any(),any());verify(dao,never()).settle(any());}
 @Test void guestCheckoutNeedsAllOrdersServed(){UUID order=UUID.randomUUID();when(dao.guest("qr",order)).thenReturn(Optional.of(scope));when(dao.bill(scope)).thenReturn(bill("OPEN",1));assertThrows(ResponseStatusException.class,()->service.guest("qr",order,true));verify(dao,never()).begin(any(),any());}
 @Test void qrAloneDoesNotRevealVisit(){assertThrows(ResponseStatusException.class,()->service.guest("qr",UUID.randomUUID(),false));verify(dao,never()).detail(any());}
 @Test void staffCannotChangeMerchantWithoutTenantWideManagement(){authorized(bill("OPEN",0));assertThrows(ResponseStatusException.class,()->service.merchant(tenant,branch,new Merchant("merchant@bank","Restaurant"),auth));verify(dao,never()).merchant(any(),any(),any());}
}
