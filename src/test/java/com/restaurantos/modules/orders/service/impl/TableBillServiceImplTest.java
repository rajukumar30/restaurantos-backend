package com.restaurantos.modules.orders.service.impl;
import com.restaurantos.modules.orders.dto.TableBillDto.*;
import com.restaurantos.modules.orders.repository.TableBillDao;
import com.restaurantos.modules.staff.TenantAccessService;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class TableBillServiceImplTest {
 private final TableBillDao dao=mock(TableBillDao.class);
 private final TenantAccessService access=mock(TenantAccessService.class);
 private final Authentication auth=mock(Authentication.class);
 private final TableBillServiceImpl service=new TableBillServiceImpl(dao,access);
 @Test void missingBillReturnsNotFound(){
  UUID tenant=UUID.randomUUID(),branch=UUID.randomUUID(),visit=UUID.randomUUID();
  assertThrows(ResponseStatusException.class,()->service.detail(tenant,branch,visit,auth));
  verify(access).requirePermission(auth,tenant,branch,"orders:read");
  verify(dao,never()).lines(any(),any(),any());
 }
 @Test void activeBillsAreTenantAndBranchScoped(){
  UUID tenant=UUID.randomUUID(),branch=UUID.randomUUID();
  service.active(tenant,branch,auth);
  verify(access).requireBranchInTenant(tenant,branch);
  verify(dao).active(tenant,branch);
 }
}
