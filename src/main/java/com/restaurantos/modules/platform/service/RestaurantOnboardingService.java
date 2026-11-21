package com.restaurantos.modules.platform.service;

import com.restaurantos.modules.platform.dto.RestaurantOnboardingDto.*;

public interface RestaurantOnboardingService {
    RestaurantCreated create(CreateRestaurantRequest request);
}
