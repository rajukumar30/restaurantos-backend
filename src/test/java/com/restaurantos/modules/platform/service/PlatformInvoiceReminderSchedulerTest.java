package com.restaurantos.modules.platform.service;

import com.restaurantos.modules.platform.repository.PlatformInvoiceReminderDao;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PlatformInvoiceReminderSchedulerTest {
    private final PlatformInvoiceReminderDao dao=mock(PlatformInvoiceReminderDao.class);
    @Test void disabledSchedulerNeverTouchesDatabase(){
        new PlatformInvoiceReminderScheduler(dao,false).runDaily();
        verifyNoInteractions(dao);
    }
    @Test void enabledSchedulerCreatesDueReminderEvents(){
        new PlatformInvoiceReminderScheduler(dao,true).runDaily();
        verify(dao).createDueReminders(any(LocalDate.class));
    }
}
