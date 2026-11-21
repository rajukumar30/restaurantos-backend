package com.restaurantos.modules.platform.dto;
import java.util.*;
public final class PlatformEntitlementsDto {
 private PlatformEntitlementsDto(){}
 public record Entitlement(String key,String name,String category,boolean platformAvailable,
   boolean planIncluded,boolean restaurantEnabled,boolean effectiveEnabled,String reason){}
 public record Snapshot(UUID tenantId,UUID planId,String planName,String subscriptionStatus,
   boolean accessEligible,List<Entitlement> features){}

}
