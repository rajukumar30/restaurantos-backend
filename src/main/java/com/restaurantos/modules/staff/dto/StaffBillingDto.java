package com.restaurantos.modules.staff.dto;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.UUID;
public final class StaffBillingDto {
 private StaffBillingDto(){}
 public record PaymentRequest(@NotNull UUID branchId,@NotNull @DecimalMin("0.01") BigDecimal amount,
  @NotBlank String method,@Size(max=120) String reference){}
}
