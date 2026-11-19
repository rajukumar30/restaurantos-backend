package com.restaurantos.modules.tables;

import com.restaurantos.common.api.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/public/qr")
public class PublicQrController {
    private final JdbcTemplate jdbc;

    public PublicQrController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/{qrToken}")
    public ApiResponse<PublicMenu> resolve(@PathVariable String qrToken) {
        List<PublicMenu> matches = jdbc.query("""
                SELECT rt.id AS table_id, rt.display_name AS table_name,
                       b.id AS branch_id, b.name AS branch_name, t.name AS restaurant_name,
                       rt.tenant_id
                FROM restaurant_tables rt
                JOIN branches b ON b.id = rt.branch_id AND b.tenant_id = rt.tenant_id
                JOIN tenants t ON t.id = rt.tenant_id
                WHERE rt.qr_token = ? AND rt.is_active = TRUE
                  AND b.status = 'ACTIVE' AND t.status = 'ACTIVE'
                """, (rs, row) -> new PublicMenu(
                rs.getObject("table_id", UUID.class), rs.getString("table_name"),
                rs.getObject("branch_id", UUID.class), rs.getString("branch_name"),
                rs.getString("restaurant_name"), List.of()), qrToken);
        if (matches.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "This table QR code is invalid or inactive.");
        }
        PublicMenu table = matches.get(0);
        Map<UUID, List<PublicItem>> grouped = new LinkedHashMap<>();
        jdbc.query("""
                SELECT mc.id AS category_id, mc.name AS category_name,
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
            grouped.computeIfAbsent(categoryId, ignored -> new ArrayList<>()).add(new PublicItem(
                    rs.getObject("item_id", UUID.class), rs.getString("item_name"),
                    rs.getString("description"), rs.getBigDecimal("price"),
                    rs.getString("dietary_type"), rs.getInt("prep_time_minutes")));
        }, table.tenantId(), table.branchId());
        List<PublicCategory> categories = jdbc.query("""
                SELECT id, name FROM menu_categories
                WHERE tenant_id = ? AND is_active = TRUE ORDER BY sort_order, name
                """, (rs, row) -> {
            UUID id = rs.getObject("id", UUID.class);
            return new PublicCategory(id, rs.getString("name"), grouped.getOrDefault(id, List.of()));
        }, table.tenantId());
        return ApiResponse.success("Public menu loaded", new PublicMenu(
                table.tableId(), table.tableName(), table.branchId(), table.branchName(),
                table.restaurantName(), categories));
    }

    public record PublicMenu(UUID tableId, String tableName, UUID branchId, String branchName,
                             String restaurantName, List<PublicCategory> categories) {}
    public record PublicCategory(UUID id, String name, List<PublicItem> items) {}
    public record PublicItem(UUID id, String name, String description, java.math.BigDecimal price,
                             String dietaryType, int prepTimeMinutes) {}
}
