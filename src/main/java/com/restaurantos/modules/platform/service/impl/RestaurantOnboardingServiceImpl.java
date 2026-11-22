package com.restaurantos.modules.platform.service.impl;

import com.restaurantos.modules.platform.dto.RestaurantOnboardingDto.*;
import com.restaurantos.modules.platform.repository.RestaurantOnboardingDao;
import com.restaurantos.modules.platform.service.RestaurantOnboardingService;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class RestaurantOnboardingServiceImpl implements RestaurantOnboardingService {
    private static final Set<String> RESERVED = Set.of("www","admin","api","app","support","login","mail","status","docs");
    private final RestaurantOnboardingDao dao;
    private final PasswordEncoder encoder;
    public RestaurantOnboardingServiceImpl(RestaurantOnboardingDao dao, PasswordEncoder encoder) {
        this.dao = dao; this.encoder = encoder;
    }

    @Override
    @Transactional
    public RestaurantCreated create(CreateRestaurantRequest req) {
        String slug = slugify(req.restaurantName());
        if (slug.length() > 63) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
            "Restaurant name produces a URL slug longer than 63 characters; use a shorter name.");
        if (dao.slugExists(slug)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A restaurant with this name already exists; choose a unique name.");
        }
        String subdomain = availableSubdomain(slug);
        if (dao.emailExists(req.ownerEmail().trim())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this owner email already exists.");
        }
        UUID tenantId = UUID.randomUUID(), branchId = UUID.randomUUID(),
            userId = UUID.randomUUID(), tenantUserId = UUID.randomUUID(),
            branchUserId = UUID.randomUUID(), roleId = UUID.randomUUID();
        String address = req.addressJson() == null || req.addressJson().isBlank() ? "{}" : req.addressJson();
        dao.insertTenant(tenantId, req.restaurantName().trim(), slug, subdomain);
        dao.insertBranch(branchId, tenantId, req.branchName().trim(), address, req.branchPhone());
        dao.insertOwner(userId, req.ownerName().trim(), req.ownerEmail().trim().toLowerCase(Locale.ROOT),
            req.ownerPhone(), encoder.encode(req.ownerPassword()));
        dao.insertTenantMember(tenantUserId, tenantId, userId);
        dao.insertBranchMember(branchUserId, tenantId, branchId, tenantUserId);
        dao.insertOwnerRole(roleId, tenantId);
        dao.grantAllPermissions(roleId);
        dao.assignOwnerRole(tenantId, tenantUserId, roleId);
        return new RestaurantCreated(tenantId, branchId, userId, slug, subdomain,
            "/r/" + slug + "/menu", "ACTIVE");
    }

    private String slugify(String value) {
        String slug = value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        if (slug.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
            "Restaurant name must contain letters or numbers.");
        return slug;
    }

    private String availableSubdomain(String slug) {
        String candidate = slug;
        if (candidate.length() > 63) candidate = suffix(candidate);
        if (RESERVED.contains(candidate)) candidate = "restaurant-" + candidate;
        if (dao.subdomainExists(candidate)) candidate = suffix(candidate);
        return candidate;
    }
    private String suffix(String candidate) {
        return candidate.substring(0, Math.min(candidate.length(), 54)).replaceAll("-+$", "")
            + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }
}
