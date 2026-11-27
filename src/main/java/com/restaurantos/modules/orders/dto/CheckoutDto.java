package com.restaurantos.modules.orders.dto;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
public final class CheckoutDto {
 private CheckoutDto(){}
 public record Merchant(@Size(max=150) String upiId,@Size(max=100) String merchantName){}
 public record Bill(UUID visitId,UUID tableId,String tableName,String billNumber,String status,Instant openedAt,
 BigDecimal total,BigDecimal paid,BigDecimal due,int orderCount,int unfinished,String upiId,String merchantName,Instant requestedAt){}
 public record Line(String orderNumber,String itemName,int quantity,BigDecimal lineTotal){}
 public record Detail(Bill bill,List<Line> items){}
 public record Page(List<Bill> bills,long total,int page,int size){}
 public record VisitAction(@NotNull UUID branchId){}
 public record Confirm(@NotNull UUID branchId,@NotNull UUID paymentKey,@NotNull @DecimalMin("0.01") @Digits(integer=10,fraction=2) BigDecimal amount,
 @NotBlank @Size(max=20) String method,@Size(max=120) String reference){}
 public record Allocation(UUID orderId,BigDecimal due){}
 public record Scope(UUID tenant,UUID branch,UUID table,UUID visit){}
}
