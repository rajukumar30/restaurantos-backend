package com.restaurantos.modules.platform;

import com.restaurantos.common.api.ApiResponse;
import com.restaurantos.modules.platform.dto.RestaurantOnboardingDto.*;
import com.restaurantos.modules.platform.service.RestaurantOnboardingService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/platform/restaurants")
public class RestaurantOnboardingController {
    private final RestaurantOnboardingService service;
    public RestaurantOnboardingController(RestaurantOnboardingService service) { this.service = service; }

    @PostMapping
    public ApiResponse<RestaurantCreated> create(@Valid @RequestBody CreateRestaurantRequest request) {
        return ApiResponse.success("Restaurant and owner account created", service.create(request));
    }
}
