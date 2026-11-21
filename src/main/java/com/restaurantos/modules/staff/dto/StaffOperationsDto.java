package com.restaurantos.modules.staff.dto;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
public final class StaffOperationsDto {
 private StaffOperationsDto(){}
 public record KitchenStatusRequest(@NotNull UUID branchId,@NotNull String status){}
}
