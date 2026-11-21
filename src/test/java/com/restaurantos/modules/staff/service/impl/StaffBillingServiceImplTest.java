package com.restaurantos.modules.staff.service.impl;
import com.restaurantos.modules.staff.TenantAccessService;
import com.restaurantos.modules.staff.dto.StaffBillingDto.PaymentRequest;
import com.restaurantos.modules.staff.repository.StaffBillingDao;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class StaffBillingServiceImplTest {
 private final StaffBillingDao dao=mock(StaffBillingDao.class);
 private final TenantAccessService access=mock(TenantAccessService.class);
 private final Authentication auth=mock(Authentication.class);
 private final StaffBillingServiceImpl service=new StaffBillingServiceImpl(dao,access);
 private final UUID tenant=UUID.randomUUID(),branch=UUID.randomUUID(),order=UUID.randomUUID(),actor=UUID.randomUUID();
 private void allow(){when(access.requirePermission(auth,tenant,branch,"payments:read")).thenReturn(actor);
  when(dao.cashier(tenant,branch,actor)).thenReturn(true);}
 @Test void rejectsOverpayment(){allow();
  when(dao.lockServedOrder(tenant,branch,order)).thenReturn(List.of(new BigDecimal("100")));
  when(dao.paid(tenant,branch,order)).thenReturn(new BigDecimal("80"));
  assertThrows(ResponseStatusException.class,()->service.record(tenant,order,
   new PaymentRequest(branch,new BigDecimal("30"),"CASH",null),auth));
  verify(dao,never()).insertPayment(any(),any(),any(),any(),any(),any(),any(),any());
 }
 @Test void rejectsUnservedOrder(){allow();
  assertThrows(ResponseStatusException.class,()->service.record(tenant,order,
   new PaymentRequest(branch,BigDecimal.ONE,"CASH",null),auth));
 }
 @Test void recordsRemainingBalance(){allow();
  when(dao.lockServedOrder(tenant,branch,order)).thenReturn(List.of(new BigDecimal("100")));
  when(dao.paid(tenant,branch,order)).thenReturn(new BigDecimal("20"));
  var result=service.record(tenant,order,new PaymentRequest(branch,new BigDecimal("30"),"cash",null),auth);
  assertEquals(new BigDecimal("50"),result.get("remainingBalance"));
  verify(dao).insertPayment(any(),eq(tenant),eq(branch),eq(order),eq(new BigDecimal("30")),eq("CASH"),isNull(),eq(actor));
 }
}
