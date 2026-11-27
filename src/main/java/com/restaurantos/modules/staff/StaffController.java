package com.restaurantos.modules.staff;

import com.restaurantos.common.api.ApiResponse;
import com.restaurantos.modules.staff.dto.StaffAdminDto.*;
import com.restaurantos.modules.staff.service.StaffAdministrationService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/staff")
public class StaffController {
    private final StaffAdministrationService service;
    public StaffController(StaffAdministrationService service) { this.service = service; }

    @GetMapping
    public ApiResponse<List<StaffMember>> list(@PathVariable UUID tenantId,
                                                @RequestParam(required = false) UUID branchId,
                                                Authentication authentication) {
        return ApiResponse.success("Staff loaded", service.list(tenantId, branchId, authentication));
    }
    @PostMapping
    public ApiResponse<StaffCreated> invite(@PathVariable UUID tenantId,
                                            @Valid @RequestBody InviteStaffRequest request,
                                            Authentication authentication) {
        return ApiResponse.success("Staff account created", service.invite(tenantId, request, authentication));
    }
    @PatchMapping("/{userId}")
    public ApiResponse<StaffMember> update(@PathVariable UUID tenantId,@PathVariable UUID userId,
         @Valid @RequestBody UpdateStaffRequest request,Authentication authentication){
        return ApiResponse.success("Staff updated",service.update(tenantId,userId,request,authentication));
    }
}
