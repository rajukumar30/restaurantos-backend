package com.restaurantos.modules.platform.service.impl;

import com.restaurantos.modules.platform.dto.PlatformFeatureDto.*;
import com.restaurantos.modules.platform.repository.PlatformFeatureDao;
import com.restaurantos.modules.platform.service.PlatformFeatureService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Service
public class PlatformFeatureServiceImpl implements PlatformFeatureService {
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
    private final PlatformFeatureDao dao;
    public PlatformFeatureServiceImpl(PlatformFeatureDao dao) { this.dao = dao; }

    @Override @Transactional(readOnly = true)
    public List<Feature> catalog() { return load(null); }

    @Override @Transactional(readOnly = true)
    public List<Feature> features(UUID tenantId) {
        requireTenant(tenantId);
        return load(tenantId);
    }

    @Override @Transactional
    public List<Feature> update(UUID tenantId, String featureKey, UpdateFeature request, Authentication authentication) {
        requireTenant(tenantId);
        dao.lockTenant(tenantId);
        if (authentication == null || !(authentication.getPrincipal() instanceof UUID actorId))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        List<Feature> current = load(tenantId);
        Feature target = current.stream().filter(f -> f.key().equals(featureKey)).findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown feature"));
        if (request == null || request.enabled() == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Feature selection required");
        if (request.enabled() && !target.available())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Integration is not available");
        Map<String, Boolean> proposed = new HashMap<>();
        for (Feature feature : current) proposed.put(feature.key(), feature.enabled());
        proposed.put(featureKey, request.enabled());
        for (Feature feature : current) {
            if (!Boolean.TRUE.equals(proposed.get(feature.key()))) continue;
            for (String dependency : feature.dependencies()) {
                if (!Boolean.TRUE.equals(proposed.get(dependency)))
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                        feature.key() + " requires " + dependency + ". Disable dependent features first.");
            }
        }
        if (request.expiresAt() != null && !request.expiresAt().isAfter(OffsetDateTime.now(ZoneOffset.UTC)))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Override expiry must be in the future");
        dao.audit(actorId, tenantId, featureKey, target.enabled(), request.enabled());
        dao.upsertOverride(tenantId, featureKey, request.enabled(), actorId, request.expiresAt());
        return load(tenantId);
    }

    private void requireTenant(UUID tenantId) {
        if (!dao.tenantExists(tenantId))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Restaurant not found");
    }
    private List<Feature> load(UUID tenantId) {
        return dao.load(tenantId).stream().map(row -> {
            boolean enabled = row.available() && (row.overrideEnabled() == null || row.overrideEnabled());
            return new Feature(row.key(), row.name(), row.category(), row.description(), row.available(),
                enabled, row.overrideEnabled(), DEPENDENCIES.getOrDefault(row.key(), List.of()), row.expiresAt());
        }).toList();
    }
}
