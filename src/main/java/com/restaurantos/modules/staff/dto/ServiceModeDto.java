package com.restaurantos.modules.staff.dto;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
public final class ServiceModeDto {
 private ServiceModeDto(){}
 public record ServiceModeRequest(@NotNull UUID branchId,@NotNull String mode){}
}
