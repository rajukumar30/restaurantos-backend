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
  if(!request.initiallyAvailable())dao.setAvailable(tenantId,request.branchId(),id,false);
  notifyBranch(tenantId,request.branchId(),id);
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
  var branches=authorizeShared(auth,tenantId,branchId,itemId);
  var optimized=ImageOptimizer.menuImages(image);
  var url=dao.storeImage(tenantId,itemId,optimized.detail().bytes(),optimized.thumbnail().bytes());
  branches.forEach(b->notifyBranch(tenantId,b,itemId));return new ImageUpdated(itemId,url);
 }
 @Transactional
 public ImageUpdated removeImage(UUID tenantId,UUID branchId,UUID itemId,Authentication auth){
  authorize(auth,tenantId,branchId,"menu:manage");requireItem(tenantId,branchId,itemId);
  var branches=authorizeShared(auth,tenantId,branchId,itemId);
  dao.removeImage(tenantId,itemId);branches.forEach(b->notifyBranch(tenantId,b,itemId));return new ImageUpdated(itemId,null);
 }
 private void requireItem(UUID tenantId,UUID branchId,UUID itemId){
  if(!dao.assignedItemExists(tenantId,branchId,itemId))throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Menu item not found in this branch.");
 }
 @Transactional(readOnly=true)
 public Optional<byte[]> publicImage(UUID id,boolean thumbnail){return dao.publicImage(id,thumbnail);}
 private void notifyBranch(UUID tenant,UUID branch,UUID id){
  events.publishMenuAfterCommit(new RealtimeEvent(UUID.randomUUID(),"MENU_CATALOG_CHANGED",tenant,branch,id,1,Instant.now(),Map.of()));
 }
 private List<UUID> authorizeShared(Authentication auth,UUID tenant,UUID branch,UUID item){
  requireItem(tenant,branch,item);
  var branches=dao.assignedBranches(tenant,item);
  for(UUID b:branches)if(!b.equals(branch))authorize(auth,tenant,b,"menu:manage");
  return branches;
 }
 private CatalogItem requireCatalogItem(UUID tenant,UUID branch,UUID item){
  return dao.catalogItem(tenant,branch,item).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Menu item not found."));
 }
 @Transactional(readOnly=true)
 public CatalogPage catalog(UUID tenant,CatalogQuery q,Authentication auth){
  authorize(auth,tenant,q.branchId(),"menu:read");
  if(q.search().length()>150||!Set.of("all","available","sold-out").contains(q.availability())||!Set.of("all","VEG","NON_VEG","EGG").contains(q.diet())||!Set.of("all","yes","no").contains(q.photo())||!Set.of("name","name-desc","price-low","price-high","recent").contains(q.sort())||q.page()<1||q.page()>100000||q.size()<1||q.size()>100||
   (q.minPrice()!=null&&q.minPrice().signum()<0)||(q.maxPrice()!=null&&q.maxPrice().signum()<0)||(q.minPrice()!=null&&q.maxPrice()!=null&&q.minPrice().compareTo(q.maxPrice())>0))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid catalogue filters.");
  return dao.catalog(tenant,q);
 }
 @Transactional
 public ItemUpdated edit(UUID tenant,UUID item,CreateMenuItemRequest request,MultipartFile image,boolean removeImage,Authentication auth){
  UUID branch=request.branchId();authorize(auth,tenant,branch,"menu:manage");
  var branches=authorizeShared(auth,tenant,branch,item);
  if(!dao.activeCategoryExists(tenant,request.categoryId()))throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Menu category not found.");
  String diet=request.dietaryType().trim().toUpperCase(Locale.ROOT);
  if(!Set.of("VEG","NON_VEG","EGG").contains(diet)||image!=null&&removeImage)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid item details.");
  var optimized=image==null?null:ImageOptimizer.menuImages(image);
  if(dao.editDefinition(tenant,item,request,diet)!=1||dao.updateBranchItem(tenant,branch,item,new UpdateMenuItemRequest(branch,request.price(),request.initiallyAvailable()))!=1)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Menu item not found.");
  if(optimized!=null)dao.storeImage(tenant,item,optimized.detail().bytes(),optimized.thumbnail().bytes());else if(removeImage)dao.removeImage(tenant,item);
  branches.forEach(b->notifyBranch(tenant,b,item));return new ItemUpdated(item,request.price(),request.initiallyAvailable());
 }
 @Transactional
 public ItemUpdated availability(UUID tenant,UUID item,AvailabilityRequest request,Authentication auth){
  authorize(auth,tenant,request.branchId(),"menu:manage");
  if(dao.setAvailable(tenant,request.branchId(),item,request.available())!=1)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Menu item not found.");
  var updated=requireCatalogItem(tenant,request.branchId(),item);notifyBranch(tenant,request.branchId(),item);
  return new ItemUpdated(item,updated.price(),updated.available());
 }
 @Transactional
 public ItemCreated duplicate(UUID tenant,UUID branch,UUID item,Authentication auth){
  authorize(auth,tenant,branch,"menu:manage");var source=requireCatalogItem(tenant,branch,item);
  String name=source.name().substring(0,Math.min(143,source.name().length()))+" (copy)";
  // Start unavailable so a duplicate is not accidentally offered to guests.
  var result=createItem(tenant,new CreateMenuItemRequest(branch,source.categoryId(),name,source.description(),source.price(),source.dietaryType(),source.prepTimeMinutes(),false),auth);
  dao.copyPhoto(tenant,item,result.id());return result;
 }
 @Transactional
 public BulkResult bulk(UUID tenant,BulkRequest request,Authentication auth){
  UUID branch=request.branchId();authorize(auth,tenant,branch,"menu:manage");
  var ids=new LinkedHashSet<>(request.itemIds());String action=request.action();
  if(ids.isEmpty()||ids.size()>100||!Set.of("available","sold-out","move","archive").contains(action))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid bulk action.");
  if("move".equals(action)&&(request.categoryId()==null||!dao.activeCategoryExists(tenant,request.categoryId())))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Select an active category.");
  var affected=new LinkedHashSet<UUID>();
  for(UUID id:ids){
   if("move".equals(action)||"archive".equals(action))affected.addAll(authorizeShared(auth,tenant,branch,id));
   else{requireItem(tenant,branch,id);affected.add(branch);}
  }
  for(UUID id:ids){
   int updated=switch(action){case "move"->dao.move(tenant,id,request.categoryId());case "archive"->dao.archive(tenant,id);default->dao.setAvailable(tenant,branch,id,"available".equals(action));};
   if(updated!=1)throw new ResponseStatusException(HttpStatus.CONFLICT,"An item changed. Refresh and retry.");
  }
  affected.forEach(b->notifyBranch(tenant,b,ids.getFirst()));return new BulkResult(ids.size());
 }
}
