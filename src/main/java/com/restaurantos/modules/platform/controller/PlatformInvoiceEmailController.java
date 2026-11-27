package com.restaurantos.modules.platform.controller;

import com.restaurantos.modules.platform.repository.PlatformInvoiceEmailDao;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

/**
 * Access is restricted to PLATFORM_ADMIN by SecurityConfig's /api/v1/platform/** rule.
 * Never return SMTP secrets or recipient email addresses.
 */
@RestController
@RequestMapping("/api/v1/platform/billing/email")
public class PlatformInvoiceEmailController {
    private final PlatformInvoiceEmailDao dao;
    private final boolean enabled;
    private final boolean configured;
    public PlatformInvoiceEmailController(PlatformInvoiceEmailDao dao,
        @Value("${restaurantos.billing.email-enabled:false}") boolean enabled,
        @Value("${restaurantos.billing.email-from:}") String from,
        @Value("${spring.mail.host:}") String host,
        @Value("${spring.mail.username:}") String username){
        this.dao=dao;
        this.enabled=enabled;
        this.configured=!from.isBlank()&&!host.isBlank()&&!username.isBlank();
    }

    @GetMapping("/status")
    public Map<String,Object> status(){
        return Map.of("success",true,"data",Map.of(
            "enabled",enabled,"configured",configured,
            "deliverySummary",dao.deliverySummary(),
            "recentDeliveries",dao.recentDeliveries()));
    }
}
