package com.restaurantos.modules.platform.service.impl;

import com.restaurantos.modules.platform.dto.PlatformSubscriptionDto.Assign;
import com.restaurantos.modules.platform.repository.PlatformSubscriptionDao;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlatformSubscriptionServiceImplTest {
    private final PlatformSubscriptionDao dao = mock(PlatformSubscriptionDao.class);
    private final PlatformSubscriptionServiceImpl service = new PlatformSubscriptionServiceImpl(dao);
    private final UUID tenant = UUID.randomUUID(), plan = UUID.randomUUID(), actor = UUID.randomUUID();
    private UsernamePasswordAuthenticationToken auth() {
        return new UsernamePasswordAuthenticationToken(actor, null);
    }

    @Test void rejectsInvalidDatesWithoutWriting() {
        Assign request = new Assign(tenant, plan, "MONTHLY", LocalDate.of(2026,1,10),
            LocalDate.of(2026,1,9), null);
        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> service.assign(request, auth()));
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
        verifyNoInteractions(dao);
    }

    @Test void missingPlanIsConflict() {
        Assign request = new Assign(tenant, plan, "ANNUAL", LocalDate.of(2026,1,10), null, null);
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
            () -> service.assign(request, auth())).getStatusCode());
        verify(dao).isActivePlan(plan);
        verify(dao, never()).insert(any());
    }

    @Test void assignmentCancelsPreviousSubscriptionBeforeInsertAndAudit() {
        Assign request = new Assign(tenant, plan, "MONTHLY", LocalDate.of(2026,1,10), null, null);
        UUID subscriptionId = UUID.randomUUID();
        when(dao.isActivePlan(plan)).thenReturn(true);
        when(dao.tenantExists(tenant)).thenReturn(true);
        when(dao.insert(request)).thenReturn(subscriptionId);
        service.assign(request, auth());
        var order = inOrder(dao);
        order.verify(dao).lockTenant(tenant);
        order.verify(dao).cancelActiveForTenant(tenant);
        order.verify(dao).insert(request);
        order.verify(dao).audit(actor, tenant, "SUBSCRIPTION_ASSIGNED");
    }

    @Test void cancelUnknownSubscriptionReturnsNotFound() {
        UUID id = UUID.randomUUID();
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
            () -> service.cancel(id, auth())).getStatusCode());
        verify(dao, never()).cancelActive(any());
    }

    @Test void unauthenticatedCannotMutate() {
        assertEquals(HttpStatus.UNAUTHORIZED, assertThrows(ResponseStatusException.class,
            () -> service.cancel(UUID.randomUUID(), null)).getStatusCode());
        verifyNoInteractions(dao);
    }
}
