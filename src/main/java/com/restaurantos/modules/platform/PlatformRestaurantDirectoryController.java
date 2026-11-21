package com.restaurantos.modules.platform;

import com.restaurantos.common.api.ApiResponse;
import com.restaurantos.modules.platform.dto.PlatformRestaurantDto.*;
import com.restaurantos.modules.platform.service.PlatformRestaurantDirectoryService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/platform/restaurants")
public class PlatformRestaurantDirectoryController {
    private final PlatformRestaurantDirectoryService service;
    public PlatformRestaurantDirectoryController(PlatformRestaurantDirectoryService service) {
        this.service = service;
    }
    @GetMapping
    public ApiResponse<List<Restaurant>> list(@RequestParam(defaultValue = "") String search,
                                               @RequestParam(defaultValue = "0") int page) {
        return ApiResponse.success("Restaurants", service.list(search, page));
    }
    @GetMapping("/{tenantId}")
    public ApiResponse<Restaurant> details(@PathVariable UUID tenantId) {
        return ApiResponse.success("Restaurant details", service.details(tenantId));
    }
    @GetMapping("/{tenantId}/profile")
    public ApiResponse<RestaurantProfile> profile(@PathVariable UUID tenantId) {
        return ApiResponse.success("Restaurant profile", service.profile(tenantId));
    }
    @PatchMapping("/{tenantId}")
    public ApiResponse<Restaurant> update(@PathVariable UUID tenantId, @RequestBody UpdateRestaurant request,
                                          Authentication authentication) {
        return ApiResponse.success("Restaurant updated", service.update(tenantId, request, authentication));
    }
    @PatchMapping("/{tenantId}/branches/{branchId}")
    public ApiResponse<BranchDetail> updateBranch(@PathVariable UUID tenantId, @PathVariable UUID branchId,
                                                  @RequestBody UpdateBranch request, Authentication authentication) {
        return ApiResponse.success("Branch updated", service.updateBranch(tenantId, branchId, request, authentication));
    }
}
