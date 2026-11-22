package com.restaurantos.modules.platform.service;

import com.restaurantos.modules.platform.repository.PlatformInvoiceDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

/**
 * Opt-in invoice issuance. One daily pass issues only invoices whose billing
 * anniversary falls within the next seven days, including today.
 * Database uniqueness makes retries safe across multiple app instances.
 */
@Component
public class PlatformInvoiceScheduler {
    private static final Logger log=LoggerFactory.getLogger(PlatformInvoiceScheduler.class);
    private final PlatformInvoiceDao dao;
    private final PlatformInvoiceService service;
    private final boolean enabled;

    public PlatformInvoiceScheduler(PlatformInvoiceDao dao, PlatformInvoiceService service,
                                    @Value("${restaurantos.billing.auto-invoice-enabled:false}") boolean enabled) {
        this.dao=dao;
        this.service=service;
        this.enabled=enabled;
    }

    @Scheduled(cron="${restaurantos.billing.invoice-cron:0 15 1 * * *}",zone="UTC")
    public void runDaily() {
        if(!enabled)return;
        LocalDate today=LocalDate.now(ZoneOffset.UTC);
        var candidates=dao.dueCandidates(today);
        if(candidates.size()>1000) {
            log.error("Invoice scheduler candidate limit exceeded; no invoices issued. Review pagination.");
            return;
        }
        int issued=0,failed=0;
        for(Map<String,Object> candidate:candidates) {
            UUID subscriptionId=(UUID)candidate.get("id");
            LocalDate starts=(LocalDate)candidate.get("startsOn");
            String cycle=(String)candidate.get("billingCycle");
            LocalDate anniversary=null;
            for(int offset=0;offset<=7;offset++){
                LocalDate target=today.plusDays(offset);
                long periods="MONTHLY".equals(cycle)
                    ?ChronoUnit.MONTHS.between(starts.withDayOfMonth(1),target.withDayOfMonth(1))
                    :ChronoUnit.YEARS.between(starts,target);
                if(periods<0)continue;
                LocalDate calculated="MONTHLY".equals(cycle)?starts.plusMonths(periods):starts.plusYears(periods);
                if(target.equals(calculated)){anniversary=target;break;}
            }
            if(anniversary==null)continue;
            try {
                service.issue(subscriptionId,anniversary);
                issued++;
            }catch(Exception ex) {
                failed++;
                log.warn("Automatic invoice issuance failed for subscription {}: {}",subscriptionId,ex.toString());
            }
        }
        log.info("Invoice scheduler completed for {}: issued/reused={}, failed={}",today,issued,failed);
    }
}
