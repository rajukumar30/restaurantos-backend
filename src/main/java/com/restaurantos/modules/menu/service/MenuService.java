package com.restaurantos.modules.menu.service;
import com.restaurantos.modules.menu.dto.MenuDto.*;
import org.springframework.security.core.Authentication;
import java.util.*;
public interface MenuService {
 List<MenuCategory> getMenu(UUID tenantId,UUID branchId,Authentication authentication);
 CategoryCreated createCategory(UUID tenantId,CreateCategoryRequest request,Authentication authentication);
 ItemCreated createItem(UUID tenantId,CreateMenuItemRequest request,Authentication authentication);
 ItemUpdated updateBranchItem(UUID tenantId,UUID itemId,UpdateMenuItemRequest request,Authentication authentication);
}
