package com.restaurantos.modules.orders.entity;
import java.util.UUID;
public record QrTable(UUID tableId,UUID tenantId,UUID branchId,String displayName){}
