package com.restaurantos.modules.platform.service;
import com.restaurantos.common.api.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;


import com.restaurantos.modules.platform.dto.PlatformAuditDto.*;
public interface PlatformAuditService {List<AuditEvent> list(int page,UUID tenantId);}
