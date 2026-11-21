package com.restaurantos.modules.platform.directory.dto;
import java.time.OffsetDateTime;
import java.util.UUID;
public record MembershipResponse(UUID membershipId,UUID userId,String name,String email,String phone,String accountStatus,String membershipStatus,UUID tenantId,String restaurantName,String roles,OffsetDateTime joinedAt){}
