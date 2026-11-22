package com.restaurantos.modules.platform.service.impl;

import com.restaurantos.modules.platform.controller.PlatformBillingController.PendingPaymentRequest;
import com.restaurantos.modules.platform.repository.PlatformBillingDao;
import com.restaurantos.modules.platform.service.PlatformBillingService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class PlatformBillingServiceImpl implements PlatformBillingService {
    private final PlatformBillingDao dao;
    public PlatformBillingServiceImpl(PlatformBillingDao dao) { this.dao = dao; }

    @Override
    @Transactional
    public Map<String, Object> createPending(PendingPaymentRequest request) {
        if (request == null || request.tenantId() == null || request.subscriptionId() == null
            || request.amountPaise() == null || request.amountPaise() <= 0
            || request.idempotencyKey() == null || !request.idempotencyKey().matches("[A-Za-z0-9_-]{12,100}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid payment request");
        }
        if (!dao.subscriptionBelongsToTenant(request.subscriptionId(), request.tenantId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Subscription not found for restaurant");
        }
        return dao.createPending(request.tenantId(), request.subscriptionId(), request.amountPaise(), request.idempotencyKey());
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> paymentByKey(String key) {
        if (key == null || !key.matches("[A-Za-z0-9_-]{12,100}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid idempotency key");
        }
        Map<String, Object> payment = dao.findByIdempotencyKey(key);
        if (payment == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment attempt not found");
        return payment;
    }

    @Override
    @Transactional
    public Map<String, Object> markFailed(UUID paymentId, UUID actorId) {
        return dao.markFailed(paymentId, actorId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> payments(UUID tenantId) {
        return dao.recentPayments(tenantId);
    }
}
