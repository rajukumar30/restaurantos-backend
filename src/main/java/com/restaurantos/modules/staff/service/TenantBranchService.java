package com.restaurantos.modules.staff.service;

import com.restaurantos.modules.staff.dto.TenantBranchDto.BranchSummary;
import org.springframework.security.core.Authentication;
import java.util.List;
import java.util.UUID;

public interface TenantBranchService {
    List<BranchSummary> list(UUID tenantId, Authentication authentication);
}
