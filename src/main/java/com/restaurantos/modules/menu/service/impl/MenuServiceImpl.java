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
@Service
public class MenuServiceImpl implements MenuService {
 private final MenuDao dao;
 private final TenantAccessService access;
 public MenuServiceImpl(MenuDao dao,TenantAccessService access){this.dao=dao;this.access=access;}
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
  return new ItemUpdated(itemId,request.price(),request.available());
 }
}
