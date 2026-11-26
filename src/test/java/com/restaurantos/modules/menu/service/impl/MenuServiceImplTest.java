package com.restaurantos.modules.menu.service.impl;
import com.restaurantos.modules.menu.dto.MenuDto.*;
import com.restaurantos.modules.menu.repository.MenuDao;
import com.restaurantos.modules.staff.TenantAccessService;
import com.restaurantos.modules.realtime.service.RealtimeEventPublisher;
import com.restaurantos.modules.realtime.dto.RealtimeEvent;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class MenuServiceImplTest {
 private final MenuDao dao=mock(MenuDao.class);
 private final TenantAccessService access=mock(TenantAccessService.class);
 private final Authentication auth=mock(Authentication.class);
 private final RealtimeEventPublisher events=mock(RealtimeEventPublisher.class);
 private final MenuServiceImpl service=new MenuServiceImpl(dao,access,events);
 @Test void rejectsDuplicateCategory(){
  UUID tenant=UUID.randomUUID(),branch=UUID.randomUUID();
  when(dao.categoryNameExists(tenant,"Lunch")).thenReturn(true);
  assertThrows(ResponseStatusException.class,()->service.createCategory(tenant,new CreateCategoryRequest(branch," Lunch ",null,0),auth));
  verify(dao,never()).insertCategory(any(),any(),any(),any(),anyInt());
 }
 @Test void rejectsUnknownDietaryTypeBeforeWrite(){
  UUID tenant=UUID.randomUUID(),branch=UUID.randomUUID(),category=UUID.randomUUID();
  when(dao.activeCategoryExists(tenant,category)).thenReturn(true);
  var request=new CreateMenuItemRequest(branch,category,"Soup",null,new BigDecimal("99"),"UNKNOWN",10);
  assertThrows(ResponseStatusException.class,()->service.createItem(tenant,request,auth));
  verify(dao,never()).insertItem(any(),any(),any(),any());
 }
 @Test void rejectsBranchItemUpdateWhenNotAssigned(){
  UUID tenant=UUID.randomUUID(),branch=UUID.randomUUID(),item=UUID.randomUUID();
  var request=new UpdateMenuItemRequest(branch,new BigDecimal("120"),true);
  assertThrows(ResponseStatusException.class,()->service.updateBranchItem(tenant,item,request,auth));
 }
 @Test void publishesSoldOutUpdateForCorrectBranch(){
  UUID tenant=UUID.randomUUID(),branch=UUID.randomUUID(),item=UUID.randomUUID();
  var request=new UpdateMenuItemRequest(branch,new BigDecimal("120"),false);
  when(dao.updateBranchItem(tenant,branch,item,request)).thenReturn(1);
  assertFalse(service.updateBranchItem(tenant,item,request,auth).available());
  var capture=ArgumentCaptor.forClass(RealtimeEvent.class);verify(events).publishMenuAfterCommit(capture.capture());
  assertEquals(branch,capture.getValue().branchId());assertEquals(item,capture.getValue().resourceId());assertEquals("false",capture.getValue().payload().get("available"));
 }
 @Test void checksTenantPermissionBeforeRead(){
  UUID tenant=UUID.randomUUID(),branch=UUID.randomUUID();
  service.getMenu(tenant,branch,auth);
  verify(access).requirePermission(auth,tenant,branch,"menu:read");
  verify(access).requireBranchInTenant(tenant,branch);
  verify(dao).findMenu(tenant,branch);
 }
}
