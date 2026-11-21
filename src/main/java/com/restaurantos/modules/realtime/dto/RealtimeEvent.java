package com.restaurantos.modules.realtime.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record RealtimeEvent(UUID eventId, String type, UUID tenantId, UUID branchId,
                            UUID resourceId, int version, Instant occurredAt, Map<String, String> payload) {
    public static RealtimeEvent orderChanged(String type, UUID tenantId, UUID branchId, UUID orderId) {
        return new RealtimeEvent(UUID.randomUUID(), type, tenantId, branchId, orderId, 1,
                Instant.now(), Map.of());
    }
}
