package com.restaurantos.modules.orders;

import com.restaurantos.common.api.ApiResponse;
import com.restaurantos.modules.staff.TenantAccessService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
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
    private final JdbcTemplate jdbc;
    private final TenantAccessService access;

    public OrderController(JdbcTemplate jdbc, TenantAccessService access) {
        this.jdbc = jdbc;
        this.access = access;
    }

    @PostMapping("/api/v1/public/qr/{qrToken}/orders")
    @Transactional
    public ApiResponse<OrderReceipt> placeQrOrder(@PathVariable String qrToken,
                                                   @Valid @RequestBody PlaceOrderRequest request) {
        List<QrTable> tables = jdbc.query("""
                SELECT rt.id AS table_id, rt.tenant_id, rt.branch_id, rt.display_name
                FROM restaurant_tables rt
                JOIN branches b ON b.id = rt.branch_id AND b.tenant_id = rt.tenant_id
                JOIN tenants t ON t.id = rt.tenant_id
                WHERE rt.qr_token = ? AND rt.is_active = TRUE
                  AND b.status = 'ACTIVE' AND t.status = 'ACTIVE'
                """, (rs, row) -> new QrTable(
                rs.getObject("table_id", UUID.class), rs.getObject("tenant_id", UUID.class),
                rs.getObject("branch_id", UUID.class), rs.getString("display_name")), qrToken);
        if (tables.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "This table QR code is invalid or inactive.");
        QrTable table = tables.get(0);

        Map<UUID, Integer> quantities = new LinkedHashMap<>();
        for (OrderLineRequest line : request.items()) {
            quantities.merge(line.menuItemId(), line.quantity(), Integer::sum);
        }
        if (quantities.values().stream().anyMatch(quantity -> quantity > 20)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A maximum of 20 units per menu item can be ordered.");
        }

        List<ValidatedOrderLine> lines = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (Map.Entry<UUID, Integer> entry : quantities.entrySet()) {
            List<ValidatedOrderLine> available = jdbc.query("""
                    SELECT mi.id, mi.name,
                           COALESCE(bmi.price_override, mi.base_price) AS price
                    FROM menu_items mi
                    JOIN menu_categories mc ON mc.tenant_id = mi.tenant_id AND mc.id = mi.category_id
                    JOIN branch_menu_items bmi ON bmi.tenant_id = mi.tenant_id AND bmi.menu_item_id = mi.id
                    WHERE mi.tenant_id = ? AND mi.id = ? AND bmi.branch_id = ?
                      AND mi.is_active = TRUE AND mc.is_active = TRUE AND bmi.is_available = TRUE
                    """, (rs, row) -> new ValidatedOrderLine(
                    rs.getObject("id", UUID.class), rs.getString("name"),
                    rs.getBigDecimal("price"), entry.getValue()), table.tenantId(), entry.getKey(), table.branchId());
            if (available.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "One or more selected menu items are no longer available.");
            }
            ValidatedOrderLine line = available.get(0);
            lines.add(line);
            total = total.add(line.unitPrice().multiply(BigDecimal.valueOf(line.quantity())));
        }

        UUID orderId = UUID.randomUUID();
        String orderNumber = "R" + UUID.randomUUID().toString().replace("-", "").substring(0, 9).toUpperCase();
        jdbc.update("""
                INSERT INTO restaurant_orders
                    (id, tenant_id, branch_id, table_id, order_number, customer_name, notes, total_amount)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, orderId, table.tenantId(), table.branchId(), table.tableId(), orderNumber,
                blankToNull(request.customerName()), blankToNull(request.notes()), total);
        for (ValidatedOrderLine line : lines) {
            jdbc.update("""
                    INSERT INTO restaurant_order_items
                        (tenant_id, order_id, menu_item_id, item_name, quantity, unit_price, line_total)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """, table.tenantId(), orderId, line.menuItemId(), line.itemName(),
                    line.quantity(), line.unitPrice(),
                    line.unitPrice().multiply(BigDecimal.valueOf(line.quantity())));
        }
        return ApiResponse.success("Order placed successfully",
                new OrderReceipt(orderId, orderNumber, table.displayName(), total, "NEW"));
    }

    @GetMapping("/api/v1/tenants/{tenantId}/orders")
    public ApiResponse<List<OrderSummary>> listOrders(@PathVariable UUID tenantId,
                                                       @RequestParam UUID branchId,
                                                       Authentication authentication) {
        access.requirePermission(authentication, tenantId, branchId, "orders:read");
        access.requireBranchInTenant(tenantId, branchId);
        List<OrderSummary> orders = jdbc.query("""
                SELECT o.id, o.order_number, o.status, o.total_amount, o.created_at,
                       rt.display_name AS table_name, COUNT(oi.id) AS line_count,
                       COALESCE(SUM(oi.quantity), 0) AS item_count,
                       COALESCE(string_agg(oi.item_name || ' × ' || oi.quantity, ', ' ORDER BY oi.created_at), '') AS items_summary
                FROM restaurant_orders o
                JOIN restaurant_tables rt ON rt.tenant_id = o.tenant_id AND rt.id = o.table_id
                LEFT JOIN restaurant_order_items oi ON oi.tenant_id = o.tenant_id AND oi.order_id = o.id
                WHERE o.tenant_id = ? AND o.branch_id = ?
                GROUP BY o.id, rt.display_name
                ORDER BY o.created_at DESC
                LIMIT 100
                """, (rs, row) -> new OrderSummary(
                rs.getObject("id", UUID.class), rs.getString("order_number"),
                rs.getString("table_name"), rs.getString("status"),
                rs.getBigDecimal("total_amount"), rs.getInt("line_count"),
                rs.getInt("item_count"), rs.getString("items_summary"),
                rs.getTimestamp("created_at").toInstant()), tenantId, branchId);
        return ApiResponse.success("Orders loaded", orders);
    }

    @PatchMapping("/api/v1/tenants/{tenantId}/orders/{orderId}/status")
    @Transactional
    public ApiResponse<OrderStatusUpdated> updateStatus(@PathVariable UUID tenantId,
                                                         @PathVariable UUID orderId,
                                                         @Valid @RequestBody UpdateOrderStatusRequest request,
                                                         Authentication authentication) {
        access.requirePermission(authentication, tenantId, request.branchId(), "orders:manage");
        access.requireBranchInTenant(tenantId, request.branchId());
        String status = request.status().trim().toUpperCase();
        if (!List.of("ACCEPTED", "PREPARING", "READY", "SERVED", "CANCELLED").contains(status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported order status.");
        }
        int updated = jdbc.update("""
                UPDATE restaurant_orders SET status = ?, updated_at = NOW()
                WHERE tenant_id = ? AND branch_id = ? AND id = ? AND status <> 'CANCELLED'
                """, status, tenantId, request.branchId(), orderId);
        if (updated == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found or already cancelled.");
        return ApiResponse.success("Order status updated", new OrderStatusUpdated(orderId, status));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record PlaceOrderRequest(@NotEmpty List<@Valid OrderLineRequest> items,
                                    @Size(max = 120) String customerName,
                                    @Size(max = 500) String notes) {}
    public record OrderLineRequest(@NotNull UUID menuItemId, @Min(1) @Max(20) int quantity) {}
    public record UpdateOrderStatusRequest(@NotNull UUID branchId, @NotNull @Size(max = 20) String status) {}
    public record OrderReceipt(UUID orderId, String orderNumber, String tableName,
                               BigDecimal totalAmount, String status) {}
    public record OrderSummary(UUID id, String orderNumber, String tableName, String status,
                               BigDecimal totalAmount, int lineCount, int itemCount, String itemsSummary,
                               java.time.Instant createdAt) {}
    public record OrderStatusUpdated(UUID orderId, String status) {}
    private record QrTable(UUID tableId, UUID tenantId, UUID branchId, String displayName) {}
    private record ValidatedOrderLine(UUID menuItemId, String itemName, BigDecimal unitPrice, int quantity) {}
}
