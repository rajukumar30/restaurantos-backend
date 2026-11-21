package com.restaurantos.modules.staff;

import com.restaurantos.modules.staff.repository.TenantAccessDao;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.util.UUID;

@Service
public class TenantAccessService {
    private final TenantAccessDao dao;
    public TenantAccessService(TenantAccessDao dao) { this.dao = dao; }

    public UUID requirePermission(Authentication authentication, UUID tenantId, UUID branchId, String permission) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UUID userId))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required.");
        if (!dao.hasPermission(userId, tenantId, branchId, permission))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have permission for this tenant or branch.");
        return userId;
    }
    public boolean hasTenantWidePermission(Authentication authentication, UUID tenantId, String permission) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UUID userId)) return false;
        return dao.hasTenantWidePermission(userId, tenantId, permission);
    }
    public void requireBranchInTenant(UUID tenantId, UUID branchId) {
        if (!dao.activeBranchExists(tenantId, branchId))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Branch not found in this restaurant.");
    }
}
