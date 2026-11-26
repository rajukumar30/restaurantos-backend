package com.restaurantos.modules.menu.service.impl;
import com.restaurantos.modules.menu.dto.MenuDto.*;
import com.restaurantos.modules.menu.repository.MenuDao;
import com.restaurantos.modules.menu.service.MenuService;
import com.restaurantos.modules.staff.TenantAccessService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import com.restaurantos.common.image.ImageOptimizer;
import com.restaurantos.modules.realtime.dto.RealtimeEvent;
import com.restaurantos.modules.realtime.service.RealtimeEventPublisher;
import java.time.Instant;
import org.springframework.web.multipart.MultipartFile;
@Service
public class MenuServiceImpl implements MenuService {
 private final MenuDao dao;
 private final TenantAccessService access;
 private final RealtimeEventPublisher events;
 public MenuServiceImpl(MenuDao dao,TenantAccessService access,RealtimeEventPublisher events){this.dao=dao;this.access=access;this.events=events;}
 private void authorize(Authentication auth,UUID tenantId,UUID branchId,String permission){
  access.requirePermission(auth,tenantId,branchId,permission);
  access.requireBranchInTenant(tenantId,branchId);
 }
 @Transactional(readOnly=true)
 public List<MenuCategory> getMenu(UUID tenantId,UUID branchId,Authentication auth){
  authorize(auth,tenantId,branchId,"menu:read");
  return dao.findMenu(tenantId,branchId);
 }
 @Transactional
 public CategoryCreated createCategory(UUID tenantId,CreateCategoryRequest request,Authentication auth){
  authorize(auth,tenantId,request.branchId(),"menu:manage");
  String name=request.name().trim();
  if(dao.categoryNameExists(tenantId,name))
   throw new ResponseStatusException(HttpStatus.CONFLICT,"A menu category with this name already exists.");
  UUID id=UUID.randomUUID();
  dao.insertCategory(id,tenantId,name,request.description(),request.sortOrder());
  return new CategoryCreated(id,name);
 }
 @Transactional
 public ItemCreated createItem(UUID tenantId,CreateMenuItemRequest request,Authentication auth){
  authorize(auth,tenantId,request.branchId(),"menu:manage");
  if(!dao.activeCategoryExists(tenantId,request.categoryId()))
   throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Menu category not found.");
  String dietaryType=request.dietaryType().trim().toUpperCase(java.util.Locale.ROOT);
  if(!List.of("VEG","NON_VEG","EGG").contains(dietaryType))
   throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Dietary type must be VEG, NON_VEG or EGG.");
  UUID id=UUID.randomUUID();
  dao.insertItem(id,tenantId,request,dietaryType);
  dao.assignItemToBranch(tenantId,request.branchId(),id);
  return new ItemCreated(id,request.categoryId(),request.name().trim(),request.price(),dietaryType);
 }
 @Transactional
 public ItemUpdated updateBranchItem(UUID tenantId,UUID itemId,UpdateMenuItemRequest request,Authentication auth){
  authorize(auth,tenantId,request.branchId(),"menu:manage");
  if(dao.updateBranchItem(tenantId,request.branchId(),itemId,request)==0)
   throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Menu item is not assigned to this branch.");
  events.publishMenuAfterCommit(new RealtimeEvent(UUID.randomUUID(),"MENU_ITEM_UPDATED",tenantId,request.branchId(),itemId,1,Instant.now(),Map.of("available",Boolean.toString(request.available()),"price",request.price().toPlainString())));
  return new ItemUpdated(itemId,request.price(),request.available());
 }
 @Transactional
 public ItemCreated createItemWithImage(UUID tenantId,CreateMenuItemRequest request,MultipartFile image,Authentication auth){
  authorize(auth,tenantId,request.branchId(),"menu:manage");
  var optimized=ImageOptimizer.menuImages(image);
  ItemCreated created=createItem(tenantId,request,auth);
  dao.storeImage(tenantId,created.id(),optimized.detail().bytes(),optimized.thumbnail().bytes());
  return created;
 }
 @Transactional
 public ImageUpdated replaceImage(UUID tenantId,UUID branchId,UUID itemId,MultipartFile image,Authentication auth){
  authorize(auth,tenantId,branchId,"menu:manage");
  requireItem(tenantId,branchId,itemId);
  var optimized=ImageOptimizer.menuImages(image);
  return new ImageUpdated(itemId,dao.storeImage(tenantId,itemId,optimized.detail().bytes(),optimized.thumbnail().bytes()));
 }
 @Transactional
 public ImageUpdated removeImage(UUID tenantId,UUID branchId,UUID itemId,Authentication auth){
  authorize(auth,tenantId,branchId,"menu:manage");requireItem(tenantId,branchId,itemId);
  dao.removeImage(tenantId,itemId);return new ImageUpdated(itemId,null);
 }
 private void requireItem(UUID tenantId,UUID branchId,UUID itemId){
  if(!dao.assignedItemExists(tenantId,branchId,itemId))throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Menu item not found in this branch.");
 }
 @Transactional(readOnly=true)
 public Optional<byte[]> publicImage(UUID id,boolean thumbnail){return dao.publicImage(id,thumbnail);}
}
