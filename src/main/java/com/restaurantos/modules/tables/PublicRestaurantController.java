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
@RequestMapping("/api/v1/public/restaurants")
public class PublicRestaurantController {
    private final JdbcTemplate jdbc;

    public PublicRestaurantController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/{slug}/menu")
    public ApiResponse<RestaurantMenu> getMenu(@PathVariable String slug,
                                                @RequestParam(required = false) UUID branchId) {
        List<RestaurantHeader> restaurants = jdbc.query("""
                SELECT t.id, t.name, t.slug
                FROM tenants t
                WHERE t.slug = ? AND t.status = 'ACTIVE'
                """, (rs, row) -> new RestaurantHeader(
                rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("slug")), slug);
        if (restaurants.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Restaurant not found or inactive.");
        }
        RestaurantHeader restaurant = restaurants.get(0);

        List<BranchSummary> branches = jdbc.query("""
                SELECT id, name
                FROM branches
                WHERE tenant_id = ? AND status = 'ACTIVE'
                ORDER BY name
                """, (rs, row) -> new BranchSummary(
                rs.getObject("id", UUID.class), rs.getString("name")), restaurant.id());
        if (branches.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "This restaurant has no active branches.");
        }
        BranchSummary selectedBranch = branchId == null
                ? branches.get(0)
                : branches.stream().filter(branch -> branch.id().equals(branchId)).findFirst()
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Branch not found for this restaurant."));

        Map<UUID, List<MenuItem>> itemsByCategory = new LinkedHashMap<>();
        jdbc.query("""
                SELECT mi.id, mi.category_id, mi.name, mi.description,
                       COALESCE(bmi.price_override, mi.base_price) AS price,
                       mi.dietary_type, mi.prep_time_minutes
                FROM menu_items mi
                JOIN branch_menu_items bmi
                  ON bmi.tenant_id = mi.tenant_id AND bmi.menu_item_id = mi.id
                WHERE mi.tenant_id = ? AND bmi.branch_id = ?
                  AND mi.is_active = TRUE AND bmi.is_available = TRUE
                ORDER BY mi.name
                """, rs -> {
            UUID categoryId = rs.getObject("category_id", UUID.class);
            itemsByCategory.computeIfAbsent(categoryId, ignored -> new ArrayList<>()).add(
                    new MenuItem(rs.getObject("id", UUID.class), rs.getString("name"),
                            rs.getString("description"), rs.getBigDecimal("price"),
                            rs.getString("dietary_type"), rs.getInt("prep_time_minutes")));
        }, restaurant.id(), selectedBranch.id());

        List<MenuCategory> categories = jdbc.query("""
                SELECT id, name, description, sort_order
                FROM menu_categories
                WHERE tenant_id = ? AND is_active = TRUE
                ORDER BY sort_order, name
                """, (rs, row) -> {
            UUID id = rs.getObject("id", UUID.class);
            return new MenuCategory(id, rs.getString("name"), rs.getString("description"),
                    rs.getInt("sort_order"), itemsByCategory.getOrDefault(id, List.of()));
        }, restaurant.id());

        return ApiResponse.success("Restaurant menu loaded",
                new RestaurantMenu(restaurant.name(), restaurant.slug(), branches, selectedBranch.id(),
                        selectedBranch.name(), categories));
    }

    private record RestaurantHeader(UUID id, String name, String slug) {}
    public record BranchSummary(UUID id, String name) {}
    public record RestaurantMenu(String restaurantName, String slug, List<BranchSummary> branches,
                                 UUID branchId, String branchName, List<MenuCategory> categories) {}
    public record MenuCategory(UUID id, String name, String description, int sortOrder, List<MenuItem> items) {}
    public record MenuItem(UUID id, String name, String description, java.math.BigDecimal price,
                           String dietaryType, int prepTimeMinutes) {}
}
