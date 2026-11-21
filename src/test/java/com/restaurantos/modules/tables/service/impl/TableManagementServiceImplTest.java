package com.restaurantos.modules.tables.service.impl;
import com.restaurantos.modules.tables.dto.TableDto.*;
import com.restaurantos.modules.tables.repository.TableDao;
import com.restaurantos.modules.staff.TenantAccessService;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class TableManagementServiceImplTest {
 private final TableDao dao=mock(TableDao.class);
 private final TenantAccessService access=mock(TenantAccessService.class);
 private final Authentication auth=mock(Authentication.class);
 private final TableManagementServiceImpl service=new TableManagementServiceImpl(dao,access);
 @Test void duplicateTableNumberRejectedBeforeInsert(){
  UUID tenant=UUID.randomUUID(),branch=UUID.randomUUID();
  when(dao.numberExists(tenant,branch,"7")).thenReturn(true);
  assertThrows(ResponseStatusException.class,()->service.create(tenant,new CreateTableRequest(branch," 7 ",null,4),auth));
  verify(dao,never()).insert(any(),any(),any(),any(),any(),anyInt(),any());
 }
 @Test void generatedTokenIsNontrivialAndDefaultNameApplied(){
  UUID tenant=UUID.randomUUID(),branch=UUID.randomUUID();
  RestaurantTable table=service.create(tenant,new CreateTableRequest(branch," 7 ",null,4),auth);
  assertEquals("Table 7",table.displayName());
  assertEquals(43,table.qrToken().length());
  verify(dao).insert(eq(table.id()),eq(tenant),eq(branch),eq("7"),eq("Table 7"),eq(4),eq(table.qrToken()));
 }
 @Test void updateRequiresExistingTable(){
  UUID tenant=UUID.randomUUID(),branch=UUID.randomUUID(),table=UUID.randomUUID();
  assertThrows(ResponseStatusException.class,()->service.update(tenant,table,new UpdateTableRequest(branch,"Table 2",4,false),auth));
 }
 @Test void invalidQrTokenRejected(){
  assertThrows(ResponseStatusException.class,()->service.publicMenu(UUID.randomUUID(),"invalid"));
 }
}
