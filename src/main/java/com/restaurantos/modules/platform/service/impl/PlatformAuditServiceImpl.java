package com.restaurantos.modules.platform.service.impl;
import com.restaurantos.common.api.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;


import com.restaurantos.modules.platform.dto.PlatformAuditDto.*;
import com.restaurantos.modules.platform.service.PlatformAuditService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class PlatformAuditServiceImpl implements PlatformAuditService {
 private final PlatformAuditDao dao;
 public PlatformAuditServiceImpl(PlatformAuditDao dao){this.dao=dao;}
 @Transactional(readOnly=true)
         public List<AuditEvent> list(int page,
                                              UUID tenantId) { return dao.list(page,tenantId); }

}
