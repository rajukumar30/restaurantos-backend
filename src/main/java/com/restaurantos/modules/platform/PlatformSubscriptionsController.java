package com.restaurantos.modules.platform;

import com.restaurantos.common.api.ApiResponse;
import com.restaurantos.modules.platform.dto.PlatformSubscriptionDto.*;
import com.restaurantos.modules.platform.service.PlatformSubscriptionService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/platform/subscriptions")
public class PlatformSubscriptionsController {
    private final PlatformSubscriptionService service;
    public PlatformSubscriptionsController(PlatformSubscriptionService service) { this.service = service; }

    @GetMapping
    public ApiResponse<List<Subscription>> list(@RequestParam(required = false) UUID tenantId) {
        return ApiResponse.success("Restaurant subscriptions", service.list(tenantId));
    }

    @PostMapping
    public ApiResponse<Subscription> assign(@RequestBody Assign req, Authentication authentication) {
        return ApiResponse.success("Restaurant subscription assigned", service.assign(req, authentication));
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<Subscription> cancel(@PathVariable UUID id, Authentication authentication) {
        return ApiResponse.success("Subscription cancelled", service.cancel(id, authentication));
    }
}
