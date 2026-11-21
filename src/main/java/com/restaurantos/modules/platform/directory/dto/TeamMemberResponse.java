package com.restaurantos.modules.platform.directory.dto;
import java.time.OffsetDateTime;
import java.util.UUID;
public record TeamMemberResponse(UUID id,String name,String email,String phone,String accountStatus,String adminStatus,String roles,OffsetDateTime addedAt){}
