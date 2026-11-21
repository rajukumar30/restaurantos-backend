package com.restaurantos.modules.orders;

import com.restaurantos.common.api.ApiResponse;
import com.restaurantos.modules.orders.dto.OrderDto.*;
import com.restaurantos.modules.orders.service.OrderQueryService;
import com.restaurantos.modules.orders.service.OrderPlacementService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
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
