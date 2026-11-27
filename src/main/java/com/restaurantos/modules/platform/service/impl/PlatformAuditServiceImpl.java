package com.restaurantos.modules.platform.service.impl;

import com.restaurantos.modules.platform.PlatformAuditDao;
import com.restaurantos.modules.platform.dto.PlatformAuditDto.AuditEvent;
import com.restaurantos.modules.platform.service.PlatformAuditService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.UUID;

@Service
public class PlatformAuditServiceImpl implements PlatformAuditService {
    private final PlatformAuditDao dao;
    public PlatformAuditServiceImpl(PlatformAuditDao dao) { this.dao=dao; }
    @Override @Transactional(readOnly=true)
    public List<AuditEvent> list(int page, UUID tenantId) {
        if (page<0 || page>100000)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid page");
        return dao.list(page,tenantId);
    }
}
