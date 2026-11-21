package com.restaurantos.modules.orders;

import com.restaurantos.common.api.ApiResponse;
import com.restaurantos.modules.orders.dto.OrderDto.*;
import com.restaurantos.modules.orders.service.OrderQueryService;
import com.restaurantos.modules.orders.service.OrderPlacementService;
import com.restaurantos.modules.staff.TenantAccessService;
import com.restaurantos.modules.platform.RestaurantFeatureAccessService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
public class OrderController {
    private final OrderQueryService orderQueries;
    private final OrderPlacementService orderPlacement;

    public OrderController(OrderQueryService orderQueries,OrderPlacementService orderPlacement){
        this.orderQueries=orderQueries;
        this.orderPlacement=orderPlacement;
    }

    @PostMapping("/api/v1/public/qr/{qrToken}/orders")
    public ApiResponse<OrderReceipt> placeQrOrder(@PathVariable String qrToken,
            @Valid @RequestBody PlaceOrderRequest request,Authentication authentication){
        return ApiResponse.success("Order placed successfully",orderPlacement.place(qrToken,request));
    }

    @GetMapping("/api/v1/public/qr/{qrToken}/orders/{orderId}")
    public ApiResponse<PublicOrderStatus> getPublicOrderStatus(@PathVariable String qrToken,
            @PathVariable UUID orderId,Authentication authentication){
        return ApiResponse.success("Order status loaded",orderQueries.publicStatus(qrToken,orderId));
    }

    @GetMapping("/api/v1/tenants/{tenantId}/orders")
    public ApiResponse<List<OrderSummary>> listOrders(@PathVariable UUID tenantId,
            @RequestParam UUID branchId,Authentication authentication){
        return ApiResponse.success("Orders loaded",orderQueries.list(tenantId,branchId,authentication));
    }

    @PatchMapping("/api/v1/tenants/{tenantId}/orders/{orderId}/status")
    public ApiResponse<OrderStatusUpdated> updateStatus(@PathVariable UUID tenantId,
            @PathVariable UUID orderId,@Valid @RequestBody UpdateOrderStatusRequest request,
            Authentication authentication){
        return ApiResponse.success("Order status updated",orderQueries.updateStatus(tenantId,orderId,request,authentication));
    }

}
