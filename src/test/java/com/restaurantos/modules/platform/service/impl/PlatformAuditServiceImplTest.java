package com.restaurantos.modules.platform.service.impl;

import com.restaurantos.modules.platform.PlatformAuditDao;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlatformAuditServiceImplTest {
    private final PlatformAuditDao dao=mock(PlatformAuditDao.class);
    private final PlatformAuditServiceImpl service=new PlatformAuditServiceImpl(dao);
    @Test void validationPreventsInvalidDatabasePagination() {
        assertEquals(HttpStatus.BAD_REQUEST,assertThrows(ResponseStatusException.class,
            () -> service.list(-1,null)).getStatusCode());
        verifyNoInteractions(dao);
    }
    @Test void listsTenantAuditWithPaging() {
        UUID tenant=UUID.randomUUID();
        service.list(2,tenant);
        verify(dao).list(2,tenant);
    }
}
