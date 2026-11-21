package com.restaurantos.modules.platform;

import com.restaurantos.modules.platform.repository.RestaurantFeatureAccessDao;
import com.restaurantos.modules.platform.repository.RestaurantFeatureAccessDao.Access;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

/** Server-side feature gate. Disabled by default; unassigned legacy tenants remain grandfathered in. */
@Service
public class RestaurantFeatureAccessService {
    private final RestaurantFeatureAccessDao dao;
    private final boolean enforcementEnabled;
    private final int graceDays;
    public RestaurantFeatureAccessService(RestaurantFeatureAccessDao dao,
        @Value("${restaurantos.entitlements.enforce:false}") boolean enforcementEnabled,
        @Value("${restaurantos.entitlements.grace-days:7}") int graceDays) {
        this.dao=dao; this.enforcementEnabled=enforcementEnabled;
        this.graceDays=Math.max(0,Math.min(90,graceDays));
    }
    public void require(UUID tenantId, String featureKey) {
        if (!enforcementEnabled) return;
        Access a = dao.latestActive(tenantId, featureKey);
        if (dao.activeGrant(tenantId, featureKey)) return;
        if (a == null) {
            if (!dao.hasAnySubscription(tenantId)) return;
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "No active subscription. Please contact restaurant support.");
        }
        LocalDate now = LocalDate.now(ZoneOffset.UTC);
        if (now.isBefore(a.startsOn()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Subscription has not started. Contact restaurant support.");
        if (a.endsOn()!=null && now.isAfter(a.endsOn().plusDays(graceDays)))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Subscription expired. Please renew your restaurant plan.");
        if (!a.available() || !a.included() || !a.restaurantEnabled())
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "This feature is not enabled for your restaurant plan.");
    }
}
