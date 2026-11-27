package com.restaurantos.modules.menu;
import com.restaurantos.common.api.ApiResponse;
import com.restaurantos.modules.menu.dto.MenuDto.*;
import com.restaurantos.modules.menu.service.MenuService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import java.math.BigDecimal;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;
@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/menu")
public class MenuController {
 private final MenuService service;
 public MenuController(MenuService service){this.service=service;}
 @GetMapping
 public ApiResponse<List<MenuCategory>> getMenu(@PathVariable UUID tenantId,@RequestParam UUID branchId,Authentication auth){
  return ApiResponse.success("Menu loaded",service.getMenu(tenantId,branchId,auth));
 }
 @PostMapping("/categories")
 public ApiResponse<CategoryCreated> createCategory(@PathVariable UUID tenantId,@Valid @RequestBody CreateCategoryRequest request,Authentication auth){
  return ApiResponse.success("Menu category created",service.createCategory(tenantId,request,auth));
 }
 @PostMapping(value="/items",consumes=MediaType.APPLICATION_JSON_VALUE)
 public ApiResponse<ItemCreated> createItem(@PathVariable UUID tenantId,@Valid @RequestBody CreateMenuItemRequest request,Authentication auth){
  return ApiResponse.success("Menu item created",service.createItem(tenantId,request,auth));
 }
 @PatchMapping("/items/{itemId}")
 public ApiResponse<ItemUpdated> updateBranchItem(@PathVariable UUID tenantId,@PathVariable UUID itemId,@Valid @RequestBody UpdateMenuItemRequest request,Authentication auth){
  return ApiResponse.success("Menu item updated",service.updateBranchItem(tenantId,itemId,request,auth));
 }
 @PostMapping(value="/items",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
 public ApiResponse<ItemCreated> createItemWithImage(@PathVariable UUID tenantId,@Valid @RequestPart("item") CreateMenuItemRequest request,@RequestPart("image") MultipartFile image,Authentication auth){
  return ApiResponse.success("Menu item created",service.createItemWithImage(tenantId,request,image,auth));
 }
 @PutMapping(value="/items/{itemId}/image",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
 public ApiResponse<ImageUpdated> replaceImage(@PathVariable UUID tenantId,@PathVariable UUID itemId,@RequestParam UUID branchId,@RequestPart("image") MultipartFile image,Authentication auth){
  return ApiResponse.success("Dish image updated",service.replaceImage(tenantId,branchId,itemId,image,auth));
 }
 @DeleteMapping("/items/{itemId}/image")
 public ApiResponse<ImageUpdated> removeImage(@PathVariable UUID tenantId,@PathVariable UUID itemId,@RequestParam UUID branchId,Authentication auth){
  return ApiResponse.success("Dish image removed",service.removeImage(tenantId,branchId,itemId,auth));
 }
 @GetMapping("/catalog")
 public ApiResponse<CatalogPage> catalog(@PathVariable UUID tenantId,@RequestParam UUID branchId,
 @RequestParam(defaultValue="") String search,@RequestParam(required=false) UUID categoryId,
 @RequestParam(defaultValue="all") String availability,@RequestParam(defaultValue="all") String diet,
 @RequestParam(defaultValue="all") String photo,@RequestParam(required=false) BigDecimal minPrice,@RequestParam(required=false) BigDecimal maxPrice,
 @RequestParam(defaultValue="name") String sort,@RequestParam(defaultValue="false") boolean grouped,
 @RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="25") int size,Authentication auth){
  return ApiResponse.success("Catalogue loaded",service.catalog(tenantId,new CatalogQuery(branchId,search,categoryId,availability,diet,photo,minPrice,maxPrice,sort,grouped,page,size),auth));
 }
 @PutMapping(value="/items/{itemId}",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
 public ApiResponse<ItemUpdated> edit(@PathVariable UUID tenantId,@PathVariable UUID itemId,@Valid @RequestPart("item") CreateMenuItemRequest request,@RequestPart(value="image",required=false) MultipartFile image,@RequestParam(defaultValue="false") boolean removeImage,Authentication auth){
  return ApiResponse.success("Item updated",service.edit(tenantId,itemId,request,image,removeImage,auth));
 }
 @PatchMapping("/items/{itemId}/availability")
 public ApiResponse<ItemUpdated> availability(@PathVariable UUID tenantId,@PathVariable UUID itemId,@Valid @RequestBody AvailabilityRequest request,Authentication auth){
  return ApiResponse.success("Availability updated",service.availability(tenantId,itemId,request,auth));
 }
 @PostMapping("/items/{itemId}/duplicate")
 public ApiResponse<ItemCreated> duplicate(@PathVariable UUID tenantId,@PathVariable UUID itemId,@RequestParam UUID branchId,Authentication auth){
  return ApiResponse.success("Item duplicated",service.duplicate(tenantId,branchId,itemId,auth));
 }
 @PostMapping("/items/bulk")
 public ApiResponse<BulkResult> bulk(@PathVariable UUID tenantId,@Valid @RequestBody BulkRequest request,Authentication auth){
  return ApiResponse.success("Items updated",service.bulk(tenantId,request,auth));
 }
}
