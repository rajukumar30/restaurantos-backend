package com.restaurantos.modules.staff.dto;

import jakarta.validation.constraints.*;
import java.util.UUID;

public final class StaffAdminDto {
    private StaffAdminDto() {}
    public record InviteStaffRequest(
        @NotBlank @Size(max=150) String name,
        @NotBlank @Email @Size(max=255) String email,
        @Size(max=20) String phone,
        @NotBlank @Size(min=12,max=100) String temporaryPassword,
        @NotNull UUID branchId,
        @NotBlank String roleKey) {}
    public record StaffMember(UUID userId, String name, String email, String phone,
                              String accountStatus, String roles, String branches) {}
    public record StaffCreated(UUID userId, UUID tenantUserId, UUID branchId,
                               String roleKey, String status) {}
    public record UpdateStaffRequest(@NotNull UUID branchId,
        @NotBlank @Size(max=150) String name,@Size(max=20) String phone,
        @NotBlank String roleKey,@NotBlank String accountStatus) {}
}
