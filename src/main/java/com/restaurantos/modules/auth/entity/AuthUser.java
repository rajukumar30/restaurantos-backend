package com.restaurantos.modules.auth.entity;
import java.util.UUID;
public record AuthUser(UUID id,String name,String email,String passwordHash,String status){}
