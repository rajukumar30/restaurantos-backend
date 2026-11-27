package com.restaurantos.modules.ordersecurity.dto;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
public final class OrderSecurityDto{
 private OrderSecurityDto(){}
 public record Settings(String mode,BigDecimal maxOrderAmount,BigDecimal maxVisitAmount,int maxUnits){}
 public record Update(@NotNull UUID branchId,@NotBlank @Pattern(regexp="SECURE|EXPRESS|STRICT") String mode,
 @NotNull @DecimalMin("100") @DecimalMax("5000") @Digits(integer=4,fraction=2) BigDecimal maxOrderAmount,
 @NotNull @DecimalMin("100") @DecimalMax("10000") @Digits(integer=5,fraction=2) BigDecimal maxVisitAmount,
 @Min(1) @Max(20) int maxUnits,boolean acknowledgeRisk){}
 public record Access(String mode){}
 public record Joined(String token,UUID visitId,Instant expiresAt,boolean verified){}
 public record Review(UUID id,UUID tableId,UUID visitId,String tableName,String orderNumber,String items,BigDecimal total,String reason,Instant createdAt,Instant expiresAt){}
 public record Decision(@NotNull UUID branchId,@NotBlank @Pattern(regexp="APPROVE|REJECT") String action,@NotBlank @Size(max=250) String reason){}
 public record Stored(Review review,UUID sessionId,String key,String hash,String json,String status){}
}
