package com.restaurantos.architecture;

import com.restaurantos.modules.orders.BillReceiptController;
import com.restaurantos.modules.platform.*;
import com.restaurantos.modules.staff.StaffController;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.*;
import java.lang.reflect.Method;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Makes unintentional changes to legacy REST route mappings fail CI. */
class BackendRouteContractTest {
    @Test void remainingModulesKeepAllPublicRoutes() {
        Set<String> expected = Set.of(
            "GET /api/v1/platform/subscriptions",
            "POST /api/v1/platform/subscriptions",
            "POST /api/v1/platform/subscriptions/{id}/cancel",
            "POST /api/v1/platform/restaurants",
            "GET /api/v1/platform/restaurants",
            "GET /api/v1/platform/restaurants/{tenantId}",
            "GET /api/v1/platform/restaurants/{tenantId}/profile",
            "PATCH /api/v1/platform/restaurants/{tenantId}",
            "PATCH /api/v1/platform/restaurants/{tenantId}/branches/{branchId}",
            "GET /api/v1/platform/features",
            "GET /api/v1/platform/restaurants/{tenantId}/features",
            "PUT /api/v1/platform/restaurants/{tenantId}/features/{featureKey}",
            "GET /api/v1/tenants/{tenantId}/staff",
            "POST /api/v1/tenants/{tenantId}/staff",
            "POST /api/v1/tenants/{tenantId}/bills/{visitId}/receipt-link",
            "GET /api/v1/public/receipts/{token}.pdf"
        );
        Set<String> actual = new HashSet<>();
        for (Class<?> controller : List.of(
            PlatformSubscriptionsController.class, RestaurantOnboardingController.class,
            PlatformRestaurantDirectoryController.class, PlatformFeatureController.class,
            StaffController.class, BillReceiptController.class)) {
            RequestMapping prefix = controller.getAnnotation(RequestMapping.class);
            String base = prefix == null || prefix.value().length == 0 ? "" : prefix.value()[0];
            for (Method method : controller.getDeclaredMethods()) {
                GetMapping get=method.getAnnotation(GetMapping.class);
                PostMapping post=method.getAnnotation(PostMapping.class);
                PutMapping put=method.getAnnotation(PutMapping.class);
                PatchMapping patch=method.getAnnotation(PatchMapping.class);
                if (get!=null) actual.add("GET " + base + suffix(get.value()));
                if (post!=null) actual.add("POST " + base + suffix(post.value()));
                if (put!=null) actual.add("PUT " + base + suffix(put.value()));
                if (patch!=null) actual.add("PATCH " + base + suffix(patch.value()));
            }
        }
        assertEquals(expected, actual);
    }

    private static String suffix(String[] values) {
        return values.length==0 ? "" : values[0];
    }
}
