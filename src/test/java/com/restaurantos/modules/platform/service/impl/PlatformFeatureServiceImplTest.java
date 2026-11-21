package com.restaurantos.modules.platform.service.impl;

import com.restaurantos.modules.platform.dto.PlatformFeatureDto.*;
import com.restaurantos.modules.platform.repository.PlatformFeatureDao;
import com.restaurantos.modules.platform.repository.PlatformFeatureDao.FeatureRow;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlatformFeatureServiceImplTest {
    private final PlatformFeatureDao dao = mock(PlatformFeatureDao.class);
    private final PlatformFeatureServiceImpl service = new PlatformFeatureServiceImpl(dao);
    private final UUID tenant = UUID.randomUUID(), actor = UUID.randomUUID();
    private UsernamePasswordAuthenticationToken auth() { return new UsernamePasswordAuthenticationToken(actor,null); }
    private void features(boolean ordering) {
        when(dao.tenantExists(tenant)).thenReturn(true);
        when(dao.load(tenant)).thenReturn(List.of(
            new FeatureRow("QR_MENU","QR Menu","GUEST","",true,true),
            new FeatureRow("GUEST_ORDERING","Guest Ordering","GUEST","",true,ordering),
            new FeatureRow("KITCHEN_DISPLAY","Kitchen","STAFF","",true,true)));
    }
    @Test void deniesDisablingDependencyRequiredByEnabledFeature() {
        features(true);
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
            () -> service.update(tenant, "GUEST_ORDERING", new UpdateFeature(false), auth())).getStatusCode());
        verify(dao, never()).upsertOverride(any(),any(),anyBoolean(),any());
    }
    @Test void allowsValidChangeAndAudits() {
        features(false);
        service.update(tenant,"GUEST_ORDERING",new UpdateFeature(true),auth());
        verify(dao).upsertOverride(tenant,"GUEST_ORDERING",true,actor);
        verify(dao).audit(actor,tenant,"GUEST_ORDERING",false,true);
    }
    @Test void tenantNotFound() {
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
            () -> service.features(tenant)).getStatusCode());
        verify(dao,never()).load(any());
    }
}
