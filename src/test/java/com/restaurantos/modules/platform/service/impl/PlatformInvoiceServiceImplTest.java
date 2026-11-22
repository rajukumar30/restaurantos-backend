package com.restaurantos.modules.platform.service.impl;

import com.restaurantos.modules.platform.repository.PlatformInvoiceDao;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlatformInvoiceServiceImplTest {
    private final PlatformInvoiceDao dao=mock(PlatformInvoiceDao.class);
    private final PlatformInvoiceServiceImpl service=new PlatformInvoiceServiceImpl(dao);

    @Test void rejectsMissingSubscriptionWithoutWrites() {
        var error=assertThrows(ResponseStatusException.class,
            ()->service.issue(null,LocalDate.of(2026,11,22)));
        assertEquals(HttpStatus.BAD_REQUEST,error.getStatusCode());
        verifyNoInteractions(dao);
    }

    @Test void delegatesInvoiceCreationWithoutAcceptingClientAmount() {
        UUID id=UUID.randomUUID();
        LocalDate period=LocalDate.of(2026,11,22);
        when(dao.create(id,period)).thenReturn(Map.of("status","UNPAID","amountPaise",9900L));
        var invoice=service.issue(id,period);
        assertEquals("UNPAID",invoice.get("status"));
        verify(dao).create(id,period);
    }
}
