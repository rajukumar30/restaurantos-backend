package com.restaurantos.modules.dashboard.service.impl;
import com.restaurantos.modules.dashboard.dto.DashboardData;
import com.restaurantos.modules.dashboard.repository.DashboardDao;
import com.restaurantos.modules.dashboard.service.DashboardService;
import com.restaurantos.modules.staff.TenantAccessService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
@Service
public class DashboardServiceImpl implements DashboardService {
 private final DashboardDao dao;private final TenantAccessService access;
 public DashboardServiceImpl(DashboardDao dao,TenantAccessService access){this.dao=dao;this.access=access;}
 @Transactional(readOnly=true)
 public DashboardData load(UUID tenantId,UUID branchId,Authentication authentication){
  access.requirePermission(authentication,tenantId,branchId,"orders:read");
  access.requireBranchInTenant(tenantId,branchId);
  return dao.fetch(tenantId,branchId);
 }
}
