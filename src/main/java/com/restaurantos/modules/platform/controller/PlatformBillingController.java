package com.restaurantos.modules.platform.controller;

import com.restaurantos.modules.platform.repository.PlatformBillingDao;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/platform/billing")
public class PlatformBillingController {
    private final PlatformBillingDao dao;
    public PlatformBillingController(PlatformBillingDao dao) { this.dao = dao; }

    // SecurityConfig restricts /api/v1/platform/** to PLATFORM_ADMIN.
    @GetMapping("/payments")
    public Map<String, Object> payments(@RequestParam(required = false) UUID tenantId) {
        List<Map<String, Object>> records = dao.recentPayments(tenantId);
        return Map.of("success", true, "data", records);
    }
}
