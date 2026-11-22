package com.restaurantos.modules.platform.controller;

import com.restaurantos.modules.platform.repository.PlatformBillingDao;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/platform/billing")
public class PlatformBillingController {
    private final PlatformBillingDao dao;
    public PlatformBillingController(PlatformBillingDao dao) { this.dao = dao; }

    public record PendingPaymentRequest(UUID tenantId, UUID subscriptionId, Long amountPaise, String idempotencyKey) {}

    @PostMapping("/payments/pending")
    public Map<String, Object> createPending(@RequestBody PendingPaymentRequest request) {
        if (request == null || request.tenantId() == null || request.subscriptionId() == null
            || request.amountPaise() == null || request.amountPaise() <= 0
            || request.idempotencyKey() == null || !request.idempotencyKey().matches("[A-Za-z0-9_-]{12,100}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid payment request");
        }
        if (!dao.subscriptionBelongsToTenant(request.subscriptionId(), request.tenantId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Subscription not found for restaurant");
        }
        return Map.of("success", true, "data",
            dao.createPending(request.tenantId(), request.subscriptionId(), request.amountPaise(), request.idempotencyKey()));
    }

    // SecurityConfig restricts /api/v1/platform/** to PLATFORM_ADMIN.
    @GetMapping("/payments")
    public Map<String, Object> payments(@RequestParam(required = false) UUID tenantId) {
        List<Map<String, Object>> records = dao.recentPayments(tenantId);
        return Map.of("success", true, "data", records);
    }
}
