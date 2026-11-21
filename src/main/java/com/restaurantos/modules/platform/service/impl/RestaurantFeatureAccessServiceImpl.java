package com.restaurantos.modules.platform.service.impl;

import com.restaurantos.modules.platform.service.PlatformEntitlementsService;
import com.restaurantos.modules.platform.service.RestaurantFeatureAccessService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.util.UUID;

@Service
public class RestaurantFeatureAccessServiceImpl implements RestaurantFeatureAccessService {
 private final PlatformEntitlementsService entitlements;
 public RestaurantFeatureAccessServiceImpl(PlatformEntitlementsService entitlements){
  this.entitlements=entitlements;
 }
 @Override
 public boolean hasFeature(UUID tenantId,String featureKey){
  if(tenantId==null || featureKey==null || !featureKey.matches("[A-Z][A-Z0-9_]{0,63}"))return false;
  var snapshot=entitlements.snapshot(tenantId);
  if(!snapshot.accessEligible())return false;
  return snapshot.features().stream().anyMatch(feature->
   feature.key().equals(featureKey)&&feature.effectiveEnabled());
 }
 @Override
 public void requireFeature(UUID tenantId,String featureKey){
  if(!hasFeature(tenantId,featureKey))
   throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Feature is not enabled for this restaurant");
 }
}
