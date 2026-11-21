package com.restaurantos.modules.orders.dto;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;
import java.math.BigDecimal;
import java.util.UUID;
public final class GuestCustomerDto {
 private GuestCustomerDto(){}
 public record PhoneRequest(@Pattern(regexp="\\+91[6-9][0-9]{9}") String phone){}
 public record VerifyRequest(UUID challengeId,@Pattern(regexp="[0-9]{6}") String code){}
 public record OtpRequested(UUID challengeId,int expiresInSeconds,String demoCode){}
 public record VerifiedSession(String accessToken,String phone,Instant expiresAt){}
 public record CustomerOrder(UUID orderId,String orderNumber,String status,BigDecimal totalAmount,
  Instant createdAt,String tableName,String customerName){}
}
