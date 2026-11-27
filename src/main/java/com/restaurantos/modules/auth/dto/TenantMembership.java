package com.restaurantos.modules.auth.dto;
import java.util.UUID;
public record TenantMembership(UUID tenantId,String tenantName,String roles){}
