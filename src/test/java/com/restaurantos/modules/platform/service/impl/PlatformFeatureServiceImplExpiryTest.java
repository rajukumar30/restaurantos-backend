package com.restaurantos.modules.platform.service.impl;

import com.restaurantos.modules.platform.dto.PlatformFeatureDto.UpdateFeature;
import com.restaurantos.modules.platform.repository.PlatformFeatureDao;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlatformFeatureServiceImplExpiryTest {
    @Test
    void refusesExpiredOverrideBeforeWritingAuditOrOverride() {
        PlatformFeatureDao dao = mock(PlatformFeatureDao.class);
        UUID tenant = UUID.randomUUID();
        when(dao.tenantExists(tenant)).thenReturn(true);
        when(dao.load(tenant)).thenReturn(List.of(
            new PlatformFeatureDao.FeatureRow("QR_MENU", "QR menu", "Guest", null, true, null, null)));
        PlatformFeatureServiceImpl service = new PlatformFeatureServiceImpl(dao);
        var admin = new UsernamePasswordAuthenticationToken(UUID.randomUUID(), null);
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> service.update(tenant, "QR_MENU",
                new UpdateFeature(true, OffsetDateTime.now().minusDays(1)), admin));
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        verify(dao, never()).audit(any(), any(), anyString(), anyBoolean(), anyBoolean());
        verify(dao, never()).upsertOverride(any(), anyString(), anyBoolean(), any(), any());
    }
}
