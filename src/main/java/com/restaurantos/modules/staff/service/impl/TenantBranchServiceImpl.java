package com.restaurantos.modules.staff.service.impl;
import com.restaurantos.common.api.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;


import com.restaurantos.modules.staff.dto.TenantBranchDto.*;
import com.restaurantos.modules.staff.service.TenantBranchService;
import com.restaurantos.modules.staff.TenantBranchDao;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class TenantBranchServiceImpl implements TenantBranchService {
 private final TenantBranchDao dao;
 public TenantBranchServiceImpl(TenantBranchDao dao){this.dao=dao;}
 @Transactional(readOnly=true)
         public List<BranchSummary> list(UUID tenantId, Authentication authentication) { return dao.list(tenantId,authentication); }

}
