package com.restaurantos.modules.staff.service;

import com.restaurantos.modules.staff.dto.StaffAdminDto.*;
import org.springframework.security.core.Authentication;
import java.util.List;
import java.util.UUID;

public interface StaffAdministrationService {
    List<StaffMember> list(UUID tenantId, UUID branchId, Authentication auth);
    StaffCreated invite(UUID tenantId, InviteStaffRequest request, Authentication auth);
    StaffMember update(UUID tenantId,UUID userId,UpdateStaffRequest request,Authentication auth);
}
