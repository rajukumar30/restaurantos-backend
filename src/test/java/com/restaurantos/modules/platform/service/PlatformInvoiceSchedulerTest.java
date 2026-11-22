package com.restaurantos.modules.platform.service;

import com.restaurantos.modules.platform.repository.PlatformInvoiceDao;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.mockito.Mockito.*;

class PlatformInvoiceSchedulerTest {
    private final PlatformInvoiceDao dao=mock(PlatformInvoiceDao.class);
    private final PlatformInvoiceService service=mock(PlatformInvoiceService.class);

    @Test void disabledSchedulerDoesNotQueryDatabase() {
        new PlatformInvoiceScheduler(dao,service,false).runDaily();
        verifyNoInteractions(dao,service);
    }

    @Test void dueMonthlySubscriptionIsIssuedOnce() {
        LocalDate today=LocalDate.now(ZoneOffset.UTC);
        UUID id=UUID.randomUUID();
        when(dao.dueCandidates(today)).thenReturn(List.of(Map.of(
            "id",id,"startsOn",today,"billingCycle","MONTHLY")));
        new PlatformInvoiceScheduler(dao,service,true).runDaily();
        verify(service).issue(id,today);
    }

    @Test void noInvoiceOutsideBillingAnniversary() {
        LocalDate today=LocalDate.now(ZoneOffset.UTC);
        UUID id=UUID.randomUUID();
        when(dao.dueCandidates(today)).thenReturn(List.of(Map.of(
            "id",id,"startsOn",today.minusDays(1),"billingCycle","MONTHLY")));
        new PlatformInvoiceScheduler(dao,service,true).runDaily();
        verifyNoInteractions(service);
    }
}
