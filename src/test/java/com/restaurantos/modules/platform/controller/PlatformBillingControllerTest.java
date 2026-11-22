package com.restaurantos.modules.platform.controller;

import com.restaurantos.modules.platform.repository.PlatformBillingDao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PlatformBillingControllerTest {
    private PlatformBillingDao dao;
    private PlatformBillingController controller;

    @BeforeEach
    void setup() {
        dao = mock(PlatformBillingDao.class);
        controller = new PlatformBillingController(dao);
    }

    @Test
    void pendingPaymentRejectsInvalidAmountWithoutDatabaseWrites() {
        var request = new PlatformBillingController.PendingPaymentRequest(
            UUID.randomUUID(), UUID.randomUUID(), 0L, "valid-key-123456");
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> controller.createPending(request));
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        verifyNoInteractions(dao);
    }

    @Test
    void pendingPaymentRejectsCrossTenantSubscription() {
        var request = new PlatformBillingController.PendingPaymentRequest(
            UUID.randomUUID(), UUID.randomUUID(), 15000L, "valid-key-123456");
        when(dao.subscriptionBelongsToTenant(request.subscriptionId(), request.tenantId())).thenReturn(false);
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> controller.createPending(request));
        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
        verify(dao, never()).createPending(any(), any(), anyLong(), anyString());
    }

    @Test
    void pendingPaymentDelegatesValidatedRequest() {
        var request = new PlatformBillingController.PendingPaymentRequest(
            UUID.randomUUID(), UUID.randomUUID(), 15000L, "valid-key-123456");
        when(dao.subscriptionBelongsToTenant(request.subscriptionId(), request.tenantId())).thenReturn(true);
        when(dao.createPending(request.tenantId(), request.subscriptionId(), 15000L, request.idempotencyKey()))
            .thenReturn(Map.of("status", "PENDING"));
        var response = controller.createPending(request);
        assertEquals(true, response.get("success"));
        assertEquals(Map.of("status", "PENDING"), response.get("data"));
    }

    @Test
    void closingPaymentRequiresAuthenticatedUuidActor() {
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> controller.markFailed(UUID.randomUUID(), null));
        assertEquals(HttpStatus.UNAUTHORIZED, error.getStatusCode());
        verifyNoInteractions(dao);
    }

    @Test
    void closingPaymentUsesAuthenticatedActor() {
        UUID actor = UUID.randomUUID();
        UUID payment = UUID.randomUUID();
        var authentication = new UsernamePasswordAuthenticationToken(actor, null);
        when(dao.markFailed(payment, actor)).thenReturn(Map.of("status", "FAILED"));
        var response = controller.markFailed(payment, authentication);
        assertEquals(Map.of("status", "FAILED"), response.get("data"));
        verify(dao).markFailed(payment, actor);
    }
}
