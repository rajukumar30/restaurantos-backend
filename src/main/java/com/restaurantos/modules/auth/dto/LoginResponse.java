package com.restaurantos.modules.auth.dto;
import java.util.List;
import java.util.UUID;
public record LoginResponse(String accessToken,String tokenType,UUID userId,String name,String email,List<String> authorities,List<TenantMembership> memberships){}
