package com.restaurantos.modules.menu;
import com.restaurantos.common.api.ApiResponse;
import com.restaurantos.modules.menu.dto.MenuDto.*;
import com.restaurantos.modules.menu.service.MenuService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;
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
}
