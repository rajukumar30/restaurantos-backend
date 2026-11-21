package com.restaurantos.modules.platform.service;

import com.restaurantos.modules.platform.dto.PlatformFeatureDto.*;
import org.springframework.security.core.Authentication;
import java.util.List;
import java.util.UUID;

public interface PlatformFeatureService {
    List<Feature> catalog();
    List<Feature> features(UUID tenantId);
    List<Feature> update(UUID tenantId, String featureKey, UpdateFeature request, Authentication authentication);
}
