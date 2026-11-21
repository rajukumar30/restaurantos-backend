package com.restaurantos.modules.health;

import com.restaurantos.common.api.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

    @GetMapping
    public ApiResponse<Map<String, String>> health() {
        return ApiResponse.success("RestaurantOS API is running", Map.of(
                "application", "restaurantos-backend",
                "status", "UP"
        ));
    }
}
