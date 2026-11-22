package com.restaurantos.modules.platform.service;

import com.restaurantos.modules.platform.controller.PlatformBillingController.PendingPaymentRequest;
import com.restaurantos.modules.platform.repository.PlatformBillingDao;
import com.restaurantos.modules.platform.service.impl.PlatformBillingServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlatformBillingServiceImplTest {
    private PlatformBillingDao dao;
    private PlatformBillingServiceImpl service;

    @BeforeEach
    void setup() {
        dao = mock(PlatformBillingDao.class);
        service = new PlatformBillingServiceImpl(dao);
    }

    @Test
    void rejectsInvalidAmount() {
        var request = new PendingPaymentRequest(UUID.randomUUID(), UUID.randomUUID(), 0L, "valid-key-123456");
        var error = assertThrows(ResponseStatusException.class, () -> service.createPending(request));
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        verifyNoInteractions(dao);
    }

    @Test
    void rejectsCrossTenantSubscription() {
        var request = new PendingPaymentRequest(UUID.randomUUID(), UUID.randomUUID(), 15000L, "valid-key-123456");
        var error = assertThrows(ResponseStatusException.class, () -> service.createPending(request));
        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
        verify(dao, never()).createPending(any(), any(), anyLong(), anyString());
    }

    @Test
    void validPendingPaymentDelegatesToDao() {
        var request = new PendingPaymentRequest(UUID.randomUUID(), UUID.randomUUID(), 15000L, "valid-key-123456");
        when(dao.subscriptionBelongsToTenant(request.subscriptionId(), request.tenantId())).thenReturn(true);
        when(dao.createPending(request.tenantId(), request.subscriptionId(), 15000L, request.idempotencyKey()))
            .thenReturn(Map.of("status", "PENDING"));
        assertEquals("PENDING", service.createPending(request).get("status"));
    }

    @Test
    void rejectsUnknownPaymentKey() {
        when(dao.findByIdempotencyKey("valid-key-123456")).thenReturn(null);
        var error = assertThrows(ResponseStatusException.class, () -> service.paymentByKey("valid-key-123456"));
        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
    }
}
