package com.restaurantos.modules.service.dto;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;
public final class ServiceRequestDto {
 private ServiceRequestDto(){}
 public record CreateRequest(@NotNull String type,@Size(max=250) String notes){}
 public record UpdateRequest(@NotNull String status){}
 public record ServiceRequest(UUID id,String type,String status,String notes,Instant createdAt,String tableName){}
 public record TableInfo(UUID id,UUID tenantId,UUID branchId){}
}
