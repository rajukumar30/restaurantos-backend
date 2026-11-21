package com.restaurantos.modules.staff.service;
import com.restaurantos.common.api.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;


import com.restaurantos.modules.staff.dto.TenantBranchDto.*;
public interface TenantBranchService {List<BranchSummary> list(UUID tenantId,Authentication authentication);}
