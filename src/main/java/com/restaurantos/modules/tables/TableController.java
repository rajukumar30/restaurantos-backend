package com.restaurantos.modules.tables;

import com.restaurantos.common.api.ApiResponse;
import com.restaurantos.modules.staff.TenantAccessService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/tables")
public class TableController {
    private final JdbcTemplate jdbc;
    private final TenantAccessService access;
    private final SecureRandom secureRandom = new SecureRandom();

    public TableController(JdbcTemplate jdbc, TenantAccessService access) {
        this.jdbc = jdbc;
        this.access = access;
    }

    @GetMapping
    public ApiResponse<List<RestaurantTable>> list(@PathVariable UUID tenantId,
                                                    @RequestParam UUID branchId,
                                                    Authentication authentication) {
        access.requirePermission(authentication, tenantId, branchId, "tables:read");
        access.requireBranchInTenant(tenantId, branchId);
        List<RestaurantTable> tables = jdbc.query("""
                SELECT id, table_number, display_name, seating_capacity, qr_token, is_active
                FROM restaurant_tables
                WHERE tenant_id = ? AND branch_id = ?
                ORDER BY table_number
                """, (rs, row) -> new RestaurantTable(
                rs.getObject("id", UUID.class), rs.getString("table_number"),
                rs.getString("display_name"), rs.getInt("seating_capacity"),
                rs.getString("qr_token"), rs.getBoolean("is_active")), tenantId, branchId);
        return ApiResponse.success("Tables loaded", tables);
    }

    @PostMapping
    @Transactional
    public ApiResponse<RestaurantTable> create(@PathVariable UUID tenantId,
                                                @Valid @RequestBody CreateTableRequest request,
                                                Authentication authentication) {
        access.requirePermission(authentication, tenantId, request.branchId(), "tables:manage");
        access.requireBranchInTenant(tenantId, request.branchId());
        String number = request.tableNumber().trim();
        Integer duplicate = jdbc.queryForObject("""
                SELECT COUNT(*) FROM restaurant_tables
                WHERE tenant_id = ? AND branch_id = ? AND lower(table_number) = lower(?)
                """, Integer.class, tenantId, request.branchId(), number);
        if (duplicate != null && duplicate > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A table with this number already exists in this branch.");
        }

        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        UUID id = UUID.randomUUID();
        String displayName = request.displayName() == null || request.displayName().isBlank()
                ? "Table " + number : request.displayName().trim();
        jdbc.update("""
                INSERT INTO restaurant_tables
                    (id, tenant_id, branch_id, table_number, display_name, seating_capacity, qr_token)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, id, tenantId, request.branchId(), number, displayName, request.seatingCapacity(), token);
        return ApiResponse.success("Table created", new RestaurantTable(
                id, number, displayName, request.seatingCapacity(), token, true));
    }

    @PatchMapping("/{tableId}")
    @Transactional
    public ApiResponse<RestaurantTable> update(@PathVariable UUID tenantId,
                                                @PathVariable UUID tableId,
                                                @Valid @RequestBody UpdateTableRequest request,
                                                Authentication authentication) {
        access.requirePermission(authentication, tenantId, request.branchId(), "tables:manage");
        access.requireBranchInTenant(tenantId, request.branchId());
        int updated = jdbc.update("""
                UPDATE restaurant_tables
                SET display_name = ?, seating_capacity = ?, is_active = ?, updated_at = NOW()
                WHERE tenant_id = ? AND branch_id = ? AND id = ?
                """, request.displayName().trim(), request.seatingCapacity(), request.active(),
                tenantId, request.branchId(), tableId);
        if (updated == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Table not found in this branch.");
        return jdbc.queryForObject("""
                SELECT id, table_number, display_name, seating_capacity, qr_token, is_active
                FROM restaurant_tables WHERE tenant_id = ? AND branch_id = ? AND id = ?
                """, (rs, row) -> ApiResponse.success("Table updated", new RestaurantTable(
                rs.getObject("id", UUID.class), rs.getString("table_number"), rs.getString("display_name"),
                rs.getInt("seating_capacity"), rs.getString("qr_token"), rs.getBoolean("is_active"))),
                tenantId, request.branchId(), tableId);
    }

    @GetMapping("/public/qr/{qrToken}")
    public ApiResponse<PublicTableMenu> publicMenu(@PathVariable UUID tenantId, @PathVariable String qrToken) {
        // This endpoint is deliberately read-only and only reveals a menu for a valid active QR token.
        List<PublicTableMenu> matches = jdbc.query("""
                SELECT rt.id AS table_id, rt.display_name AS table_name,
                       b.id AS branch_id, b.name AS branch_name,
                       t.name AS restaurant_name
                FROM restaurant_tables rt
                JOIN branches b ON b.id = rt.branch_id AND b.tenant_id = rt.tenant_id
                JOIN tenants t ON t.id = rt.tenant_id
                WHERE rt.tenant_id = ? AND rt.qr_token = ?
                  AND rt.is_active = TRUE AND b.status = 'ACTIVE' AND t.status = 'ACTIVE'
                """, (rs, row) -> new PublicTableMenu(
                rs.getObject("table_id", UUID.class), rs.getString("table_name"),
                rs.getObject("branch_id", UUID.class), rs.getString("branch_name"),
                rs.getString("restaurant_name"), new ArrayList<>()), tenantId, qrToken);
        if (matches.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "This table QR code is invalid or inactive.");
        PublicTableMenu table = matches.get(0);
        Map<UUID, List<PublicMenuItem>> grouped = new LinkedHashMap<>();
        jdbc.query("""
                SELECT mc.id AS category_id, mc.name AS category_name, mc.sort_order,
                       mi.id AS item_id, mi.name AS item_name, mi.description,
                       COALESCE(bmi.price_override, mi.base_price) AS price,
                       mi.dietary_type, mi.prep_time_minutes
                FROM menu_categories mc
                JOIN menu_items mi ON mi.tenant_id = mc.tenant_id AND mi.category_id = mc.id
                JOIN branch_menu_items bmi ON bmi.tenant_id = mi.tenant_id AND bmi.menu_item_id = mi.id
                WHERE mc.tenant_id = ? AND bmi.branch_id = ?
                  AND mc.is_active = TRUE AND mi.is_active = TRUE AND bmi.is_available = TRUE
                ORDER BY mc.sort_order, mc.name, mi.name
                """, rs -> {
            UUID categoryId = rs.getObject("category_id", UUID.class);
            grouped.computeIfAbsent(categoryId, ignored -> new ArrayList<>()).add(new PublicMenuItem(
                    rs.getObject("item_id", UUID.class), rs.getString("item_name"),
                    rs.getString("description"), rs.getBigDecimal("price"),
                    rs.getString("dietary_type"), rs.getInt("prep_time_minutes")));
        }, tenantId, table.branchId());
        List<PublicMenuCategory> categories = jdbc.query("""
                SELECT id, name, sort_order FROM menu_categories
                WHERE tenant_id = ? AND is_active = TRUE ORDER BY sort_order, name
                """, (rs, row) -> new PublicMenuCategory(rs.getObject("id", UUID.class),
                rs.getString("name"), grouped.getOrDefault(rs.getObject("id", UUID.class), List.of())),
                tenantId);
        return ApiResponse.success("Public menu loaded", new PublicTableMenu(
                table.tableId(), table.tableName(), table.branchId(), table.branchName(),
                table.restaurantName(), categories));
    }

    public record CreateTableRequest(@NotNull UUID branchId,
                                     @NotBlank @Size(max = 30) String tableNumber,
                                     @Size(max = 80) String displayName,
                                     @Min(1) @Max(50) int seatingCapacity) {}
    public record UpdateTableRequest(@NotNull UUID branchId,
                                     @NotBlank @Size(max = 80) String displayName,
                                     @Min(1) @Max(50) int seatingCapacity,
                                     boolean active) {}
    public record RestaurantTable(UUID id, String tableNumber, String displayName,
                                  int seatingCapacity, String qrToken, boolean active) {}
    public record PublicTableMenu(UUID tableId, String tableName, UUID branchId,
                                  String branchName, String restaurantName,
                                  List<PublicMenuCategory> categories) {}
    public record PublicMenuCategory(UUID id, String name, List<PublicMenuItem> items) {}
    public record PublicMenuItem(UUID id, String name, String description,
                                 java.math.BigDecimal price, String dietaryType,
                                 int prepTimeMinutes) {}
}
