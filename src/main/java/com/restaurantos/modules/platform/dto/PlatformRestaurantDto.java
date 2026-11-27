package com.restaurantos.modules.platform.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Stable restaurant directory response and update contracts. */
public final class PlatformRestaurantDto {
    private PlatformRestaurantDto() {}
    public record Restaurant(UUID id, String name, String slug, String subdomain,
                             String status, OffsetDateTime createdAt) {}
    public record BranchDetail(UUID id, String name, String branchCode, String phone,
                               String status, String timezone, String address) {}
    public record OwnerDetail(UUID id, String name, String email, String phone, String status) {}
    public record RestaurantProfile(Restaurant restaurant, List<BranchDetail> branches,
                                    List<OwnerDetail> owners) {}
    public record UpdateRestaurant(String name, String status) {}
    public record UpdateBranch(String name, String phone, String status) {}
}
