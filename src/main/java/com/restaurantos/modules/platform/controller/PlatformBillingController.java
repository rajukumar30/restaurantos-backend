package com.restaurantos.modules.platform.controller;

import com.restaurantos.modules.platform.service.PlatformBillingService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/platform/billing")
public class PlatformBillingController {
    private final PlatformBillingService service;
    public PlatformBillingController(PlatformBillingService service) { this.service = service; }

    public record PendingPaymentRequest(UUID tenantId, UUID subscriptionId, Long amountPaise, String idempotencyKey) {}

    @PostMapping("/payments/pending")
    public Map<String, Object> createPending(@RequestBody PendingPaymentRequest request) {
        return Map.of("success", true, "data", service.createPending(request));
    }

    @GetMapping("/payments/by-key")
    public Map<String, Object> paymentByKey(@RequestParam String idempotencyKey) {
        return Map.of("success", true, "data", service.paymentByKey(idempotencyKey));
    }

    @PostMapping("/payments/{paymentId}/mark-failed")
    public Map<String, Object> markFailed(@PathVariable UUID paymentId, Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UUID actorId)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return Map.of("success", true, "data", service.markFailed(paymentId, actorId));
    }

    @GetMapping("/payments")
    public Map<String, Object> payments(@RequestParam(required = false) UUID tenantId) {
        return Map.of("success", true, "data", service.payments(tenantId));
    }
}
