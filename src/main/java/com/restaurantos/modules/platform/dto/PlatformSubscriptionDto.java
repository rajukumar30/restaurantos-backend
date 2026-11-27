package com.restaurantos.modules.platform.dto;

import java.time.LocalDate;
import java.util.UUID;

/** Wire-compatible DTOs for /api/v1/platform/subscriptions. */
public final class PlatformSubscriptionDto {
    private PlatformSubscriptionDto() {}
    public record Subscription(UUID id, UUID tenantId, String restaurantName, UUID planId,
                               String planName, String billingCycle, LocalDate startsOn,
                               LocalDate freeUntil, LocalDate endsOn, String status,
                               boolean freeAccessNow, String lifecycleStatus, Long customPricePaise) {
        public Subscription(UUID id, UUID tenantId, String restaurantName, UUID planId, String planName,
                            String billingCycle, LocalDate startsOn, LocalDate freeUntil, LocalDate endsOn,
                            String status, boolean freeAccessNow, String lifecycleStatus) {
            this(id,tenantId,restaurantName,planId,planName,billingCycle,startsOn,freeUntil,endsOn,status,freeAccessNow,lifecycleStatus,null);
        }
    }
    public record Assign(UUID tenantId, UUID planId, String billingCycle,
                         LocalDate startsOn, LocalDate freeUntil, LocalDate endsOn, Long customPricePaise) {
        public Assign(UUID tenantId, UUID planId, String billingCycle,
                      LocalDate startsOn, LocalDate freeUntil, LocalDate endsOn) {
            this(tenantId,planId,billingCycle,startsOn,freeUntil,endsOn,null);
        }
    }
}
