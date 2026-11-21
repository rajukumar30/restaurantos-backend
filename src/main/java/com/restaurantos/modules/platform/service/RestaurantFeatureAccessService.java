package com.restaurantos.modules.platform.service;

import java.util.UUID;

/** Centralized, fail-closed authorization for subscription-gated restaurant capabilities. */
public interface RestaurantFeatureAccessService {
 void requireFeature(UUID tenantId, String featureKey);
 boolean hasFeature(UUID tenantId, String featureKey);
}
