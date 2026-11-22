package com.restaurantos.modules.platform.service;

import com.restaurantos.modules.platform.controller.PlatformBillingController.PendingPaymentRequest;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface PlatformBillingService {
    Map<String, Object> createPending(PendingPaymentRequest request);
    Map<String, Object> paymentByKey(String idempotencyKey);
    Map<String, Object> markFailed(UUID paymentId, UUID actorId);
    List<Map<String, Object>> payments(UUID tenantId);
}
