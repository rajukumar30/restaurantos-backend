package com.restaurantos.modules.growth.dto;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
public final class GrowthDto {
 private GrowthDto(){}
 public record ReplyRequest(@NotBlank @Size(max=1000) String message){}
 public record PromotionRequest(@NotBlank @Pattern(regexp="[A-Za-z0-9_-]{3,40}") String code,
  @Size(max=500) String description,@NotBlank String discountType,
  @NotNull @DecimalMin("0.01") BigDecimal discountValue,
  @NotNull @DecimalMin("0.00") BigDecimal minimumOrderAmount,
  @NotNull OffsetDateTime startsAt,@NotNull OffsetDateTime endsAt,
  @Min(1) Integer usageLimit){}
 public record PromotionStatusRequest(@NotNull Boolean active){}
}
