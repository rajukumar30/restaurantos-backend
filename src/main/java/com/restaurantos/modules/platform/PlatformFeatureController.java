package com.restaurantos.modules.platform;

import com.restaurantos.common.api.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@RestController
@RequestMapping("/api/v1/platform")
public class PlatformFeatureController {
    private final JdbcTemplate jdbc;
    private static final Map<String, List<String>> DEPENDENCIES = Map.of(
        "GUEST_ORDERING", List.of("QR_MENU"),
        "KITCHEN_DISPLAY", List.of("GUEST_ORDERING"),
        "WAITER_DASHBOARD", List.of("GUEST_ORDERING"),
        "ORDER_TRACKING", List.of("GUEST_ORDERING"),
        "ORDER_HISTORY", List.of("GUEST_ORDERING"),
        "SERVICE_REQUESTS", List.of("QR_MENU"),
        "DINE_IN", List.of("GUEST_ORDERING"),
        "TAKEAWAY", List.of("GUEST_ORDERING")
    );

    public PlatformFeatureController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public record Feature(String key, String name, String category, String description,
                          boolean available, boolean enabled, Boolean overrideEnabled, List<String> dependencies) {}
    public record UpdateFeature(@NotNull Boolean enabled) {}

    @GetMapping("/features")
    public ApiResponse<List<Feature>> catalog() {
        return ApiResponse.success("Feature catalogue", load(null));
    }

    @GetMapping("/restaurants/{tenantId}/features")
    public ApiResponse<List<Feature>> features(@PathVariable UUID tenantId) {
        requireTenant(tenantId);
        return ApiResponse.success("Restaurant feature settings", load(tenantId));
    }

    @PutMapping("/restaurants/{tenantId}/features/{featureKey}")
    @Transactional
    public ApiResponse<List<Feature>> update(@PathVariable UUID tenantId, @PathVariable String featureKey,
                                              @Valid @RequestBody UpdateFeature request,
                                              Authentication authentication) {
        requireTenant(tenantId);
        if (authentication == null || !(authentication.getPrincipal() instanceof UUID actorId)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        List<Feature> current = load(tenantId);
        Feature target = current.stream().filter(f -> f.key().equals(featureKey)).findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown feature"));
        if (request.enabled() && !target.available()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Integration is not available");
        }
        Map<String, Boolean> proposed = new HashMap<>();
        for (Feature feature : current) proposed.put(feature.key(), feature.enabled());
        proposed.put(featureKey, request.enabled());
        for (Feature feature : current) {
            if (!proposed.get(feature.key())) continue;
            for (String dependency : feature.dependencies()) {
                if (!Boolean.TRUE.equals(proposed.get(dependency))) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                        feature.key() + " requires " + dependency + ". Disable dependent features first.");
                }
            }
        }
        jdbc.update("""
            INSERT INTO tenant_feature_overrides(tenant_id,feature_key,enabled,updated_by)
            VALUES(?,?,?,?)
            ON CONFLICT(tenant_id,feature_key) DO UPDATE SET
              enabled=EXCLUDED.enabled, updated_by=EXCLUDED.updated_by, updated_at=NOW()
            """, tenantId, featureKey, request.enabled(), actorId);
        return ApiResponse.success("Feature updated", load(tenantId));
    }

    private void requireTenant(UUID tenantId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM tenants WHERE id=?", Integer.class, tenantId);
        if (count == null || count == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Restaurant not found");
    }

    private List<Feature> load(UUID tenantId) {
        return jdbc.query("""
            SELECT f.feature_key, f.display_name, f.category, f.description, f.available,
                   o.enabled AS override_enabled
            FROM feature_catalog f
            LEFT JOIN tenant_feature_overrides o
              ON o.feature_key=f.feature_key AND o.tenant_id=?
            ORDER BY f.category, f.display_name
            """, (rs, row) -> {
            String key = rs.getString("feature_key");
            Object override = rs.getObject("override_enabled");
            boolean available = rs.getBoolean("available");
            boolean enabled = available && (override == null || (Boolean) override);
            return new Feature(key, rs.getString("display_name"), rs.getString("category"),
                rs.getString("description"), available, enabled,
                override == null ? null : (Boolean) override,
                DEPENDENCIES.getOrDefault(key, List.of()));
        }, tenantId);
    }
}
