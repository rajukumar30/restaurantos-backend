package com.restaurantos.modules.menu.service;
import com.restaurantos.modules.menu.dto.MenuDto.*;
import org.springframework.security.core.Authentication;
import java.util.*;
import org.springframework.web.multipart.MultipartFile;
public interface MenuService {
 List<MenuCategory> getMenu(UUID tenantId,UUID branchId,Authentication authentication);
 CategoryCreated createCategory(UUID tenantId,CreateCategoryRequest request,Authentication authentication);
 ItemCreated createItem(UUID tenantId,CreateMenuItemRequest request,Authentication authentication);
 ItemUpdated updateBranchItem(UUID tenantId,UUID itemId,UpdateMenuItemRequest request,Authentication authentication);
 ItemCreated createItemWithImage(UUID tenantId,CreateMenuItemRequest request,MultipartFile image,Authentication auth);
 ImageUpdated replaceImage(UUID tenantId,UUID branchId,UUID itemId,MultipartFile image,Authentication auth);
 ImageUpdated removeImage(UUID tenantId,UUID branchId,UUID itemId,Authentication auth);
 Optional<byte[]> publicImage(UUID id,boolean thumbnail);
}
