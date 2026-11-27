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
import java.util.*;
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
 @Test void rejectsInvalidCatalogueFiltersBeforeQuery(){
  UUID tenant=UUID.randomUUID(),branch=UUID.randomUUID();
  var query=new CatalogQuery(branch,"",null,"all","all","all",null,null,"name; DROP TABLE menu_items",false,1,25);
  assertThrows(ResponseStatusException.class,()->service.catalog(tenant,query,auth));
  verify(dao,never()).catalog(any(),any());
 }
 @Test void paginatedCatalogueUsesReadPermission(){
  UUID tenant=UUID.randomUUID(),branch=UUID.randomUUID();
  var query=new CatalogQuery(branch,"paneer",null,"sold-out","VEG","no",null,null,"price-low",true,1,25);
  service.catalog(tenant,query,auth);
  verify(access).requirePermission(auth,tenant,branch,"menu:read");verify(dao).catalog(tenant,query);
 }
 @Test void sharedEditDeniesUnauthorizedOtherBranchBeforeWrite(){
  UUID tenant=UUID.randomUUID(),branch=UUID.randomUUID(),other=UUID.randomUUID(),item=UUID.randomUUID();
  when(dao.assignedItemExists(tenant,branch,item)).thenReturn(true);
  when(dao.assignedBranches(tenant,item)).thenReturn(List.of(branch,other));
  doThrow(new ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN)).when(access).requirePermission(auth,tenant,other,"menu:manage");
  var request=new CreateMenuItemRequest(branch,UUID.randomUUID(),"Rice",null,new BigDecimal("120"),"VEG",15,true);
  assertThrows(ResponseStatusException.class,()->service.edit(tenant,item,request,null,false,auth));
  verify(dao,never()).editDefinition(any(),any(),any(),any());verifyNoInteractions(events);
 }
 @Test void bulkValidatesAllItemsBeforeAnyMutation(){
  UUID tenant=UUID.randomUUID(),branch=UUID.randomUUID(),first=UUID.randomUUID(),second=UUID.randomUUID();
  when(dao.assignedItemExists(tenant,branch,first)).thenReturn(true);
  assertThrows(ResponseStatusException.class,()->service.bulk(tenant,new BulkRequest(branch,List.of(first,second),"sold-out",null),auth));
  verify(dao,never()).setAvailable(any(),any(),any(),anyBoolean());
 }
 @Test void bulkDeduplicatesSelectionAndUsesOneBranchNotification(){
  UUID tenant=UUID.randomUUID(),branch=UUID.randomUUID(),item=UUID.randomUUID();
  when(dao.assignedItemExists(tenant,branch,item)).thenReturn(true);
  when(dao.setAvailable(tenant,branch,item,false)).thenReturn(1);
  assertEquals(1,service.bulk(tenant,new BulkRequest(branch,List.of(item,item),"sold-out",null),auth).updated());
  verify(dao).setAvailable(tenant,branch,item,false);verify(events).publishMenuAfterCommit(any());
 }
 @Test void duplicateStartsUnavailable(){
  UUID tenant=UUID.randomUUID(),branch=UUID.randomUUID(),item=UUID.randomUUID(),cat=UUID.randomUUID();
  var source=new CatalogItem(item,cat,"Starters","Soup",null,new BigDecimal("99"),"VEG",10,true,null);
  when(dao.catalogItem(tenant,branch,item)).thenReturn(Optional.of(source));
  when(dao.activeCategoryExists(tenant,cat)).thenReturn(true);
  var copy=service.duplicate(tenant,branch,item,auth);
  assertNotEquals(item,copy.id());assertEquals("Soup (copy)",copy.name());
  verify(dao).setAvailable(tenant,branch,copy.id(),false);verify(dao).copyPhoto(tenant,item,copy.id());
 }
}
