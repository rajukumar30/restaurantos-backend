package com.restaurantos.modules.platform.service.impl;

import com.restaurantos.modules.platform.dto.PlatformRestaurantDto.*;
import com.restaurantos.modules.platform.repository.PlatformRestaurantDirectoryDao;
import com.restaurantos.modules.platform.service.PlatformRestaurantDirectoryService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.UUID;

@Service
public class PlatformRestaurantDirectoryServiceImpl implements PlatformRestaurantDirectoryService {
    private final PlatformRestaurantDirectoryDao dao;
    public PlatformRestaurantDirectoryServiceImpl(PlatformRestaurantDirectoryDao dao) { this.dao = dao; }

    @Override @Transactional(readOnly = true)
    public List<Restaurant> list(String search, int page) {
        String term = search.strip();
        if (term.length() > 100) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Search is too long");
        if (page < 0 || page > 100000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid page");
        return dao.list(term, page);
    }
    @Override @Transactional(readOnly = true)
    public Restaurant details(UUID tenantId) { return find(tenantId); }
    @Override @Transactional(readOnly = true)
    public RestaurantProfile profile(UUID tenantId) {
        Restaurant restaurant = find(tenantId);
        return new RestaurantProfile(restaurant, dao.branches(tenantId), dao.owners(tenantId));
    }
    @Override @Transactional
    public Restaurant update(UUID tenantId, UpdateRestaurant request, Authentication authentication) {
        UUID actor = actor(authentication);
        Restaurant before = find(tenantId);
        String name = request.name() == null ? before.name() : request.name().strip();
        String status = request.status() == null ? before.status() : request.status();
        if (name.isBlank() || name.length() > 150)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Restaurant name must be 1–150 characters");
        if (!"ACTIVE".equals(status) && !"INACTIVE".equals(status))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Status must be ACTIVE or INACTIVE");
        dao.updateRestaurant(tenantId, name, status);
        dao.audit(actor, tenantId, "RESTAURANT_PROFILE_UPDATED");
        return find(tenantId);
    }
    @Override @Transactional
    public BranchDetail updateBranch(UUID tenantId, UUID branchId, UpdateBranch request, Authentication authentication) {
        UUID actor = actor(authentication);
        find(tenantId);
        BranchDetail before = dao.branchForUpdate(tenantId, branchId);
        if (before == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Branch not found");
        String name = request.name() == null ? before.name() : request.name().strip();
        String phone = request.phone() == null ? before.phone() : request.phone().strip();
        String status = request.status() == null ? before.status() : request.status();
        if (name.isBlank() || name.length() > 150)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Branch name must be 1–150 characters");
        if (phone != null && phone.length() > 30)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Phone must be at most 30 characters");
        if (!"ACTIVE".equals(status) && !"INACTIVE".equals(status))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid branch status");
        dao.updateBranch(tenantId, branchId, name, phone, status);
        dao.audit(actor, tenantId, "BRANCH_PROFILE_UPDATED");
        return new BranchDetail(before.id(), name, before.branchCode(), phone, status,
                                before.timezone(), before.address());
    }
    private Restaurant find(UUID id) {
        Restaurant restaurant = dao.find(id);
        if (restaurant == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Restaurant not found");
        return restaurant;
    }
    private UUID actor(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UUID actorId))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        return actorId;
    }
}
