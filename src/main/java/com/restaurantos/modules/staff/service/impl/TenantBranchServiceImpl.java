package com.restaurantos.modules.staff.service.impl;

import com.restaurantos.modules.staff.TenantAccessService;
import com.restaurantos.modules.staff.TenantBranchDao;
import com.restaurantos.modules.staff.dto.TenantBranchDto.BranchSummary;
import com.restaurantos.modules.staff.service.TenantBranchService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.UUID;

@Service
public class TenantBranchServiceImpl implements TenantBranchService {
    private final TenantBranchDao dao;
    private final TenantAccessService access;
    public TenantBranchServiceImpl(TenantBranchDao dao, TenantAccessService access) {
        this.dao=dao; this.access=access;
    }
    @Override @Transactional(readOnly=true)
    public List<BranchSummary> list(UUID tenantId, Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UUID userId))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required.");
        boolean tenantWide=access.hasTenantWidePermission(authentication,tenantId,"branch:read");
        if (!tenantWide && !dao.hasBranchReadPermission(tenantId,userId))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have permission to view branches.");
        return dao.list(tenantId,userId,tenantWide);
    }
}
