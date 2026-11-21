package com.restaurantos.modules.platform.dto;

import jakarta.validation.constraints.*;
import java.util.UUID;

/** Existing onboarding request and response JSON contract. */
public final class RestaurantOnboardingDto {
    private RestaurantOnboardingDto() {}
    public record CreateRestaurantRequest(
        @NotBlank @Size(max=150) String restaurantName,
        @NotBlank @Size(max=150) String branchName,
        @NotBlank @Size(max=150) String ownerName,
        @NotBlank @Email @Size(max=255) String ownerEmail,
        @NotBlank @Size(min=12,max=100) String ownerPassword,
        @Size(max=20) String ownerPhone,
        @Size(max=20) String branchPhone,
        String addressJson) {}
    public record RestaurantCreated(UUID tenantId, UUID branchId, UUID ownerUserId,
                                    String slug, String subdomain, String restaurantUrl, String status) {}
}
