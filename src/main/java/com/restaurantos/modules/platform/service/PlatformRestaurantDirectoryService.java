package com.restaurantos.modules.platform.service;

import com.restaurantos.modules.platform.dto.PlatformRestaurantDto.*;
import org.springframework.security.core.Authentication;
import java.util.List;
import java.util.UUID;

public interface PlatformRestaurantDirectoryService {
    List<Restaurant> list(String search, int page);
    Restaurant details(UUID tenantId);
    RestaurantProfile profile(UUID tenantId);
    Restaurant update(UUID tenantId, UpdateRestaurant request, Authentication authentication);
    BranchDetail updateBranch(UUID tenantId, UUID branchId, UpdateBranch request, Authentication authentication);
}
