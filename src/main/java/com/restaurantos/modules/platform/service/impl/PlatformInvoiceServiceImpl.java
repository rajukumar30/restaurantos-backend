package com.restaurantos.modules.platform.service.impl;

import com.restaurantos.modules.platform.repository.PlatformInvoiceDao;
import com.restaurantos.modules.platform.service.PlatformInvoiceService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class PlatformInvoiceServiceImpl implements PlatformInvoiceService {
    private final PlatformInvoiceDao dao;
    public PlatformInvoiceServiceImpl(PlatformInvoiceDao dao) { this.dao=dao; }
    @Override @Transactional(readOnly=true)
    public List<Map<String,Object>> list(UUID tenantId) { return dao.list(tenantId); }
    @Override @Transactional
    public Map<String,Object> issue(UUID subscriptionId,LocalDate periodStart) {
        if(subscriptionId==null || periodStart==null)
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_REQUEST,"Subscription and billing period required");
        return dao.create(subscriptionId,periodStart);
    }
}
