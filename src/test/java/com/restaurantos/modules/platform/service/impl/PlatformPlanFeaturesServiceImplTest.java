package com.restaurantos.modules.platform.service.impl;

import com.restaurantos.modules.platform.dto.PlatformPlanFeatureDto.*;
import com.restaurantos.modules.platform.repository.PlatformPlanFeaturesDao;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlatformPlanFeaturesServiceImplTest {
    private final PlatformPlanFeaturesDao dao = mock(PlatformPlanFeaturesDao.class);
    private final PlatformPlanFeaturesServiceImpl service = new PlatformPlanFeaturesServiceImpl(dao);
    private final UUID id = UUID.randomUUID();
    private final UsernamePasswordAuthenticationToken admin =
        new UsernamePasswordAuthenticationToken(UUID.randomUUID(), null);

    @Test
    void refusesChangesToPublishedPlan() {
        when(dao.status(id, true)).thenReturn(List.of("ACTIVE"));
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> service.update(id, "QR_MENU", new Update(true), admin));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verify(dao, never()).set(any(), anyString(), anyBoolean());
    }

    @Test
    void rejectsFeatureWhenDependencyNotIncluded() {
        when(dao.status(id, true)).thenReturn(List.of("DRAFT"));
        when(dao.load(id)).thenReturn(List.of(
            new PlanFeature("QR_MENU", "QR menu", "GUEST", true, false, List.of()),
            new PlanFeature("GUEST_ORDERING", "Guest ordering", "GUEST", true, false, List.of("QR_MENU"))));
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> service.update(id, "GUEST_ORDERING", new Update(true), admin));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verify(dao, never()).set(any(), anyString(), anyBoolean());
    }

    @Test
    void rejectsUnavailableFeature() {
        when(dao.status(id, true)).thenReturn(List.of("DRAFT"));
        when(dao.load(id)).thenReturn(List.of(
            new PlanFeature("UNAVAILABLE", "Unavailable", "OTHER", false, false, List.of())));
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> service.update(id, "UNAVAILABLE", new Update(true), admin));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verify(dao, never()).set(any(), anyString(), anyBoolean());
    }

    @Test
    void requiresAuthenticatedAdminPrincipal() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> service.update(id, "QR_MENU", new Update(true), null));
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        verifyNoInteractions(dao);
    }
}
