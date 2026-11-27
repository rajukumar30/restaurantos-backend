package com.restaurantos.modules.platform.service;
import com.restaurantos.modules.platform.dto.PlatformPlanFeatureDto.*;
import org.springframework.security.core.Authentication;
import java.util.*;
public interface PlatformPlanFeaturesService {
 List<PlanFeature> list(UUID id);
 List<PlanFeature> update(UUID id,String key,Update request,Authentication auth);
}
