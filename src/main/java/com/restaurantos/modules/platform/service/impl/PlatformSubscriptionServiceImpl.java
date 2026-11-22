package com.restaurantos.modules.platform.service.impl;

import com.restaurantos.modules.platform.dto.PlatformSubscriptionDto.*;
import com.restaurantos.modules.platform.repository.PlatformSubscriptionDao;
import com.restaurantos.modules.platform.service.PlatformSubscriptionService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
public class PlatformSubscriptionServiceImpl implements PlatformSubscriptionService {
    private final PlatformSubscriptionDao dao;
    public PlatformSubscriptionServiceImpl(PlatformSubscriptionDao dao) { this.dao = dao; }

    @Override
    @Transactional(readOnly = true)
    public List<Subscription> list(UUID tenantId) { return dao.list(tenantId); }

    @Override
    @Transactional
    public Subscription assign(Assign req, Authentication authentication) {
        UUID actor = actor(authentication);
        if (req == null || req.tenantId() == null || req.planId() == null || req.startsOn() == null
            || !("MONTHLY".equals(req.billingCycle()) || "ANNUAL".equals(req.billingCycle()))
            || (req.freeUntil() != null && req.freeUntil().isBefore(req.startsOn()))
            || (req.endsOn() != null && req.endsOn().isBefore(req.startsOn()))
            || (req.freeUntil() != null && req.endsOn() != null && req.freeUntil().isAfter(req.endsOn()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid subscription dates or billing cycle");
        }
        if (!dao.isActivePlan(req.planId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Select an active plan");
        }
        if (!dao.tenantExists(req.tenantId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Restaurant not found");
        }
        dao.lockTenant(req.tenantId()); // All subscriptions for a tenant serialize on this row.
        // A future-dated replacement must not cancel current access immediately.
        // Scheduled transitions require a separate activation workflow.
        if (req.startsOn().isAfter(LocalDate.now(ZoneOffset.UTC)) && dao.hasActiveSubscription(req.tenantId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Cannot schedule a replacement while an active subscription exists. Choose today or later after the current subscription ends.");
        }
        dao.cancelActiveForTenant(req.tenantId());
        UUID id = dao.insert(req);
        dao.audit(actor, req.tenantId(), "SUBSCRIPTION_ASSIGNED");
        return dao.find(id);
    }

    @Override
    @Transactional
    public Subscription cancel(UUID id, Authentication authentication) {
        UUID actor = actor(authentication);
        UUID tenantId = dao.tenantForSubscription(id);
        if (tenantId == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Subscription not found");
        }
        dao.lockTenant(tenantId);
        if (dao.cancelActive(id) == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Subscription already cancelled");
        }
        dao.audit(actor, tenantId, "SUBSCRIPTION_CANCELLED");
        return dao.find(id);
    }

    private UUID actor(Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof UUID actor)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return actor;
    }
}
