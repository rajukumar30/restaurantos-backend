package com.restaurantos.modules.platform;

import com.restaurantos.common.api.ApiResponse;
import com.restaurantos.modules.platform.dto.PlatformFeatureDto.*;
import com.restaurantos.modules.platform.service.PlatformFeatureService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/platform")
public class PlatformFeatureController {
    private final PlatformFeatureService service;
    public PlatformFeatureController(PlatformFeatureService service) { this.service = service; }

    @GetMapping("/features")
    public ApiResponse<List<Feature>> catalog() {
        return ApiResponse.success("Feature catalogue", service.catalog());
    }
    @GetMapping("/restaurants/{tenantId}/features")
    public ApiResponse<List<Feature>> features(@PathVariable UUID tenantId) {
        return ApiResponse.success("Restaurant feature settings", service.features(tenantId));
    }
    @PutMapping("/restaurants/{tenantId}/features/{featureKey}")
    public ApiResponse<List<Feature>> update(@PathVariable UUID tenantId, @PathVariable String featureKey,
                                             @Valid @RequestBody UpdateFeature request,
                                             Authentication authentication) {
        return ApiResponse.success("Feature updated", service.update(tenantId, featureKey, request, authentication));
    }
}
