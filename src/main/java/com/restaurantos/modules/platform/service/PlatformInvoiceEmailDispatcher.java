package com.restaurantos.modules.platform.service;

import com.restaurantos.modules.platform.repository.PlatformInvoiceEmailDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

@Component
@ConditionalOnProperty(name="restaurantos.billing.email-enabled",havingValue="true")
public class PlatformInvoiceEmailDispatcher {
    private static final Logger log=LoggerFactory.getLogger(PlatformInvoiceEmailDispatcher.class);
    private final PlatformInvoiceEmailDao dao;
    private final JavaMailSender mail;
    private final boolean enabled;
    private final String from;
    public PlatformInvoiceEmailDispatcher(PlatformInvoiceEmailDao dao,JavaMailSender mail,
        @Value("${restaurantos.billing.email-enabled:false}") boolean enabled,
        @Value("${restaurantos.billing.email-from:}") String from){
        this.dao=dao;this.mail=mail;this.enabled=enabled;this.from=from;
    }

    @Scheduled(cron="${restaurantos.billing.email-cron:0 */10 * * * *}",zone="UTC")
    public void dispatch(){
        if(!enabled)return;
        if(from.isBlank()){
            log.error("Invoice email dispatch disabled: restaurantos.billing.email-from is blank");
            return;
        }
        dao.enqueue();
        for(Map<String,Object> claimed:dao.claim(30)){
            UUID id=(UUID)claimed.get("id");
            try{
                Map<String,Object> item=dao.details(id);
                if(!"UNPAID".equals(item.get("status"))){
                    dao.failed(id,"Invoice no longer unpaid");
                    continue;
                }
                String recipient=(String)item.get("recipientEmail");
                if(recipient==null || recipient.isBlank())throw new IllegalStateException("Recipient missing");
                long amount=((Number)item.get("amountPaise")).longValue();
                LocalDate due=(LocalDate)item.get("dueOn");
                String name=(String)item.get("restaurantName");
                SimpleMailMessage message=new SimpleMailMessage();
                message.setFrom(from);
                message.setTo(recipient);
                message.setSubject("RestaurantOS subscription invoice reminder");
                message.setText("Hello,\n\nYour RestaurantOS subscription invoice for "+
                    name+" is "+String.format(java.util.Locale.ROOT,"INR %.2f",amount/100.0)+
                    " and is due on "+due+".\n\nPlease sign in to RestaurantOS and open Owner > Billing & invoices to review it. "+
                    "Payment links are not available yet; contact RestaurantOS support to arrange payment.\n\nRestaurantOS");
                mail.send(message);
                dao.sent(id);
            }catch(Exception ex){
                dao.failed(id,ex.getClass().getSimpleName());
                log.warn("Invoice reminder email attempt failed for outbox id {}",id);
            }
        }
    }
}
