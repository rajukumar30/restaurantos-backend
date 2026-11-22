package com.restaurantos.modules.platform.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface PlatformInvoiceService {
    List<Map<String,Object>> list(UUID tenantId);
    Map<String,Object> issue(UUID subscriptionId,LocalDate periodStart);
}
