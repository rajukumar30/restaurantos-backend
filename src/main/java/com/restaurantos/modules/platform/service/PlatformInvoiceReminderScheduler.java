package com.restaurantos.modules.platform.service;

import com.restaurantos.modules.platform.repository.PlatformInvoiceReminderDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.LocalDate;
import java.time.ZoneOffset;

@Component
public class PlatformInvoiceReminderScheduler {
    private static final Logger log=LoggerFactory.getLogger(PlatformInvoiceReminderScheduler.class);
    private final PlatformInvoiceReminderDao dao;
    private final boolean enabled;
    public PlatformInvoiceReminderScheduler(PlatformInvoiceReminderDao dao,
            @Value("${restaurantos.billing.reminders-enabled:false}") boolean enabled){
        this.dao=dao;this.enabled=enabled;
    }
    @Scheduled(cron="${restaurantos.billing.reminder-cron:0 30 1 * * *}",zone="UTC")
    public void runDaily(){
        if(!enabled)return;
        LocalDate today=LocalDate.now(ZoneOffset.UTC);
        int count=dao.createDueReminders(today);
        log.info("Created {} invoice reminder events for {}",count,today);
    }
}
