package com.restaurantos.modules.platform.service;

import com.restaurantos.modules.platform.dto.PlatformAuditDto.AuditEvent;
import java.util.List;
import java.util.UUID;

public interface PlatformAuditService {
    List<AuditEvent> list(int page, UUID tenantId);
}
