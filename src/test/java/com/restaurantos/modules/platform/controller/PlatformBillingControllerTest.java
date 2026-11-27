package com.restaurantos.modules.platform.controller;

import com.restaurantos.modules.platform.service.PlatformBillingService;
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
    private PlatformBillingService service;
    private PlatformBillingController controller;

    @BeforeEach
    void setup() {
        service = mock(PlatformBillingService.class);
        controller = new PlatformBillingController(service);
    }

    @Test
    void pendingPaymentDelegatesToService() {
        var request = new PlatformBillingController.PendingPaymentRequest(
            UUID.randomUUID(), UUID.randomUUID(), 15000L, "valid-key-123456");
        when(service.createPending(request)).thenReturn(Map.of("status", "PENDING"));
        var response = controller.createPending(request);
        assertEquals(Map.of("status", "PENDING"), response.get("data"));
        verify(service).createPending(request);
    }

    @Test
    void closingPaymentRequiresAuthenticatedUuidActor() {
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> controller.markFailed(UUID.randomUUID(), null));
        assertEquals(HttpStatus.UNAUTHORIZED, error.getStatusCode());
        verifyNoInteractions(service);
    }

    @Test
    void closingPaymentUsesAuthenticatedActor() {
        UUID actor = UUID.randomUUID();
        UUID payment = UUID.randomUUID();
        var authentication = new UsernamePasswordAuthenticationToken(actor, null);
        when(service.markFailed(payment, actor)).thenReturn(Map.of("status", "FAILED"));
        var response = controller.markFailed(payment, authentication);
        assertEquals(Map.of("status", "FAILED"), response.get("data"));
        verify(service).markFailed(payment, actor);
    }
}
