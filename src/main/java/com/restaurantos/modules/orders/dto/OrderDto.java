package com.restaurantos.modules.orders.dto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import java.math.BigDecimal;
public final class OrderDto {
 private OrderDto(){}
    public record PlaceOrderRequest(@NotEmpty List<@Valid OrderLineRequest> items,
                                    @NotBlank @Size(min = 2, max = 120) String customerName,
                                    @NotBlank @Pattern(regexp = "\\+91[6-9][0-9]{9}") String customerPhone,
                                    @Size(max = 500) String notes) {}
    public record OrderLineRequest(@NotNull UUID menuItemId, @Min(1) @Max(20) int quantity) {}
    public record UpdateOrderStatusRequest(@NotNull UUID branchId, @NotNull @Size(max = 20) String status) {}
    public record OrderReceipt(UUID orderId, String orderNumber, String tableName,
                               BigDecimal totalAmount, String status) {}
    public record PublicOrderStatus(String orderNumber, String status, BigDecimal totalAmount, java.time.Instant createdAt) {}
    public record OrderSummary(UUID id, String orderNumber, String tableName, String status,
                               BigDecimal totalAmount, int lineCount, int itemCount, String itemsSummary,
                               java.time.Instant createdAt) {}
    public record OrderStatusUpdated(UUID orderId, String status) {}

}
