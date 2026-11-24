package com.restaurantos.modules.platform;

import com.restaurantos.modules.platform.repository.TenantFeatureAccessDao;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@Service
public class TenantFeatureAccess {
    private static final Map<String,List<String>> DEPENDENCIES = Map.of(
        "SERVICE_REQUESTS",List.of("QR_MENU"),
        "GUEST_ORDERING",List.of("QR_MENU"),
        "KITCHEN_DISPLAY",List.of("GUEST_ORDERING"),
        "WAITER_DASHBOARD",List.of("GUEST_ORDERING"),
        "ORDER_TRACKING",List.of("GUEST_ORDERING"),
        "ORDER_HISTORY",List.of("GUEST_ORDERING"),
        "DINE_IN",List.of("GUEST_ORDERING"),
        "TAKEAWAY",List.of("GUEST_ORDERING")
    );
    private final TenantFeatureAccessDao dao;
    private final RestaurantFeatureAccessService subscriptionGate;
    public TenantFeatureAccess(TenantFeatureAccessDao dao, RestaurantFeatureAccessService subscriptionGate) {
        this.dao=dao; this.subscriptionGate=subscriptionGate;
    }
    public boolean enabled(UUID tenantId, String featureKey) {
        if (tenantId == null || featureKey == null) return false;
        Set<String> keys = new HashSet<>();
        if (!collect(featureKey, keys, new HashSet<>())) return false;
        return dao.allEntitled(tenantId, keys);
    }
    private boolean collect(String key, Set<String> keys, Set<String> visiting) {
        if (!visiting.add(key)) return false;
        keys.add(key);
        for (String dependency : DEPENDENCIES.getOrDefault(key,List.of())) {
            if (!collect(dependency,keys,visiting)) return false;
        }
        visiting.remove(key);
        return true;
    }
    public void require(UUID tenantId, String featureKey) {
        if (!enabled(tenantId,featureKey))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "This feature is not enabled for this restaurant");
    }
}
