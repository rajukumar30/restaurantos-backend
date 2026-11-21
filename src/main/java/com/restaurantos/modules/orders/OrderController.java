package com.restaurantos.modules.orders;

import com.restaurantos.common.api.ApiResponse;
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
    private final JdbcTemplate jdbc;
    private final TenantAccessService access;
    private final RestaurantFeatureAccessService featureAccess;

    public OrderController(JdbcTemplate jdbc, TenantAccessService access, RestaurantFeatureAccessService featureAccess) {
        this.jdbc = jdbc;
        this.access = access;
        this.featureAccess = featureAccess;
    }

    @PostMapping("/api/v1/public/qr/{qrToken}/orders")
    @Transactional
    public ApiResponse<OrderReceipt> placeQrOrder(@PathVariable String qrToken,
                                                   @Valid @RequestBody PlaceOrderRequest request,
                                                   Authentication authentication) {
        String guestName = request.customerName().trim();
        String guestPhone = request.customerPhone();
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
        featureAccess.require(table.tenantId(), "GUEST_ORDERING");

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

        // Serialize orders for the same table so only one active visit is created.
        jdbc.queryForObject("SELECT id FROM restaurant_tables WHERE id=? FOR UPDATE",
                UUID.class, table.tableId());
        List<UUID> activeVisits = jdbc.query("""
                SELECT id FROM table_visits
                WHERE tenant_id=? AND branch_id=? AND table_id=?
                  AND status IN ('OPEN','PAYMENT_PENDING','PAID')
                FOR UPDATE
                """, (rs, row) -> rs.getObject(1, UUID.class),
                table.tenantId(), table.branchId(), table.tableId());
        UUID visitId;
        if (activeVisits.isEmpty()) {
            visitId = UUID.randomUUID();
            String billNumber = "B" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
            jdbc.update("""
                    INSERT INTO table_visits(id,tenant_id,branch_id,table_id,bill_number,status)
                    VALUES (?,?,?,?,?,'OPEN')
                    """, visitId, table.tenantId(), table.branchId(), table.tableId(), billNumber);
        } else {
            visitId = activeVisits.get(0);
            String visitStatus = jdbc.queryForObject(
                    "SELECT status FROM table_visits WHERE id=?", String.class, visitId);
            if (!"OPEN".equals(visitStatus))
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "This table bill is being settled. Please contact the cashier.");
        }

        UUID orderId = UUID.randomUUID();
        String orderNumber = "R" + UUID.randomUUID().toString().replace("-", "").substring(0, 9).toUpperCase();
        jdbc.update("""
                INSERT INTO restaurant_orders
                    (id, tenant_id, branch_id, table_id, order_number, customer_name, notes, total_amount, customer_phone, table_visit_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, orderId, table.tenantId(), table.branchId(), table.tableId(), orderNumber,
                guestName, blankToNull(request.notes()), total, guestPhone, visitId);
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

    @GetMapping("/api/v1/public/qr/{qrToken}/orders/{orderId}")
    public ApiResponse<PublicOrderStatus> getPublicOrderStatus(@PathVariable String qrToken,
                                                                @PathVariable UUID orderId,
                                                                Authentication authentication) {
        List<PublicOrderStatus> orders = jdbc.query("""
                SELECT o.order_number, o.status, o.total_amount, o.created_at
                FROM restaurant_orders o
                JOIN restaurant_tables rt ON rt.id = o.table_id AND rt.tenant_id = o.tenant_id
                JOIN branches b ON b.id = o.branch_id AND b.tenant_id = o.tenant_id
                JOIN tenants t ON t.id = o.tenant_id
                WHERE o.id = ? AND rt.qr_token = ? AND rt.is_active = TRUE
                  AND b.status = 'ACTIVE' AND t.status = 'ACTIVE'
                """, (rs, row) -> new PublicOrderStatus(
                rs.getString("order_number"), rs.getString("status"),
                rs.getBigDecimal("total_amount"), rs.getTimestamp("created_at").toInstant()),
                orderId, qrToken);
        if (orders.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found for this table.");
        }
        return ApiResponse.success("Order status loaded", orders.get(0));
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
        String status = request.status().trim().toUpperCase(java.util.Locale.ROOT);
        if (!List.of("SERVED", "CANCELLED").contains(status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported order status.");
        }
        int updated = jdbc.update("""
                UPDATE restaurant_orders SET status = ?, updated_at = NOW()
                WHERE tenant_id = ? AND branch_id = ? AND id = ? AND status = ?
                """, status, tenantId, request.branchId(), orderId, status.equals("SERVED") ? "READY" : "NEW");
        if (updated == 0) throw new ResponseStatusException(HttpStatus.CONFLICT, "Order status changed or this transition is not allowed.");
        if ("SERVED".equals(status)) {
            UUID actorId=(UUID) authentication.getPrincipal();
            jdbc.update("""
                INSERT INTO restaurant_order_service_events(tenant_id,branch_id,order_id,actor_id,event_type)
                VALUES (?,?,?,?,?)
                """,tenantId,request.branchId(),orderId,actorId,"WAITER_CONFIRMED_SERVED");
        }
        return ApiResponse.success("Order status updated", new OrderStatusUpdated(orderId, status));
    }

    private UUID requireCustomer(Authentication authentication) {
        if(authentication==null || !(authentication.getPrincipal() instanceof UUID id) ||
           authentication.getAuthorities().stream().noneMatch(a->"CUSTOMER".equals(a.getAuthority())))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Customer phone login required.");
        Integer found=jdbc.queryForObject("SELECT COUNT(*) FROM restaurant_customers WHERE id=?",Integer.class,id);
        if(found==null||found==0) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Customer account not found.");
        return id;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

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
    private record QrTable(UUID tableId, UUID tenantId, UUID branchId, String displayName) {}
    private record ValidatedOrderLine(UUID menuItemId, String itemName, BigDecimal unitPrice, int quantity) {}
}
