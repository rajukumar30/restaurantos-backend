package com.restaurantos.modules.platform.service;

import com.restaurantos.modules.platform.repository.PlatformInvoiceEmailDao;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.mockito.Mockito.*;

class PlatformInvoiceEmailDispatcherTest {
    private final PlatformInvoiceEmailDao dao=mock(PlatformInvoiceEmailDao.class);
    private final JavaMailSender sender=mock(JavaMailSender.class);
    @Test void disabledDispatcherDoesNotQueueOrSend(){
        new PlatformInvoiceEmailDispatcher(dao,sender,false,"").dispatch();
        verifyNoInteractions(dao,sender);
    }
    @Test void missingSenderAddressDoesNotQueue(){
        new PlatformInvoiceEmailDispatcher(dao,sender,true,"").dispatch();
        verifyNoInteractions(dao,sender);
    }
    @Test void failedDeliveryIsRecordedForRetry(){
        UUID id=UUID.randomUUID();
        when(dao.claim(30)).thenReturn(List.of(Map.of("id",id)));
        when(dao.details(id)).thenThrow(new IllegalStateException("Unavailable"));
        new PlatformInvoiceEmailDispatcher(dao,sender,true,"billing@example.com").dispatch();
        verify(dao).failed(id,"IllegalStateException");
        verify(dao,never()).sent(id);
    }
}
