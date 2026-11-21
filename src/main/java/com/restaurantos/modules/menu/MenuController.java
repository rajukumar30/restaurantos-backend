package com.restaurantos.modules.menu;
import com.restaurantos.common.api.ApiResponse;
import com.restaurantos.modules.menu.dto.MenuDto.*;
import com.restaurantos.modules.menu.service.MenuService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;
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
 @PostMapping("/items")
 public ApiResponse<ItemCreated> createItem(@PathVariable UUID tenantId,@Valid @RequestBody CreateMenuItemRequest request,Authentication auth){
  return ApiResponse.success("Menu item created",service.createItem(tenantId,request,auth));
 }
 @PatchMapping("/items/{itemId}")
 public ApiResponse<ItemUpdated> updateBranchItem(@PathVariable UUID tenantId,@PathVariable UUID itemId,@Valid @RequestBody UpdateMenuItemRequest request,Authentication auth){
  return ApiResponse.success("Menu item updated",service.updateBranchItem(tenantId,itemId,request,auth));
 }
}
