package com.restaurantos.modules.platform;

import com.restaurantos.common.api.ApiResponse;
import com.restaurantos.modules.platform.service.PublicRestaurantDomainService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/public/restaurants/domains")
public class PublicRestaurantDomainController {
    private final PublicRestaurantDomainService service;
    public PublicRestaurantDomainController(PublicRestaurantDomainService service) { this.service = service; }

    public record RestaurantDomain(String slug) {}

    @GetMapping("/{subdomain}")
    public ApiResponse<RestaurantDomain> resolve(@PathVariable String subdomain) {
        return ApiResponse.success("Restaurant resolved", new RestaurantDomain(service.resolveSlug(subdomain)));
    }
}
