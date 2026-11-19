package com.restaurantos.modules.menu;

import com.restaurantos.common.api.ApiResponse;
import com.restaurantos.modules.staff.TenantAccessService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
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
@RequestMapping("/api/v1/tenants/{tenantId}/menu")
public class MenuController {
    private final JdbcTemplate jdbc;
    private final TenantAccessService access;

    public MenuController(JdbcTemplate jdbc, TenantAccessService access) {
        this.jdbc = jdbc;
        this.access = access;
    }

    @GetMapping
    public ApiResponse<List<MenuCategory>> getMenu(@PathVariable UUID tenantId,
                                                    @RequestParam UUID branchId,
                                                    Authentication authentication) {
        access.requirePermission(authentication, tenantId, branchId, "menu:read");
        access.requireBranchInTenant(tenantId, branchId);

        Map<UUID, List<MenuItem>> itemsByCategory = new LinkedHashMap<>();
        jdbc.query("""
                SELECT mi.id, mi.category_id, mi.name, mi.description,
                       COALESCE(bmi.price_override, mi.base_price) AS price,
                       mi.dietary_type, mi.prep_time_minutes, bmi.is_available
                FROM menu_items mi
                JOIN branch_menu_items bmi
                  ON bmi.tenant_id = mi.tenant_id AND bmi.menu_item_id = mi.id
                WHERE mi.tenant_id = ? AND bmi.branch_id = ?
                  AND mi.is_active = TRUE
                ORDER BY mi.name
                """, rs -> {
            UUID categoryId = rs.getObject("category_id", UUID.class);
            itemsByCategory.computeIfAbsent(categoryId, ignored -> new ArrayList<>()).add(
                    new MenuItem(rs.getObject("id", UUID.class), categoryId, rs.getString("name"),
                            rs.getString("description"), rs.getBigDecimal("price"),
                            rs.getString("dietary_type"), rs.getInt("prep_time_minutes"),
                            rs.getBoolean("is_available")));
        }, tenantId, branchId);

        List<MenuCategory> categories = jdbc.query("""
                SELECT id, name, description, sort_order, is_active
                FROM menu_categories
                WHERE tenant_id = ?
                ORDER BY sort_order, name
                """, (rs, row) -> {
            UUID categoryId = rs.getObject("id", UUID.class);
            return new MenuCategory(categoryId, rs.getString("name"), rs.getString("description"),
                    rs.getInt("sort_order"), rs.getBoolean("is_active"),
                    itemsByCategory.getOrDefault(categoryId, List.of()));
        }, tenantId);
        return ApiResponse.success("Menu loaded", categories);
    }

    @PostMapping("/categories")
    @Transactional
    public ApiResponse<CategoryCreated> createCategory(@PathVariable UUID tenantId,
                                                        @Valid @RequestBody CreateCategoryRequest request,
                                                        Authentication authentication) {
        access.requirePermission(authentication, tenantId, request.branchId(), "menu:manage");
        access.requireBranchInTenant(tenantId, request.branchId());
        Integer duplicate = jdbc.queryForObject(
                "SELECT COUNT(*) FROM menu_categories WHERE tenant_id = ? AND lower(name) = lower(?)",
                Integer.class, tenantId, request.name().trim());
        if (duplicate != null && duplicate > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A menu category with this name already exists.");
        }
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO menu_categories (id, tenant_id, name, description, sort_order)
                VALUES (?, ?, ?, ?, ?)
                """, id, tenantId, request.name().trim(), request.description(), request.sortOrder());
        return ApiResponse.success("Menu category created", new CategoryCreated(id, request.name().trim()));
    }

    @PostMapping("/items")
    @Transactional
    public ApiResponse<ItemCreated> createItem(@PathVariable UUID tenantId,
                                                @Valid @RequestBody CreateMenuItemRequest request,
                                                Authentication authentication) {
        access.requirePermission(authentication, tenantId, request.branchId(), "menu:manage");
        access.requireBranchInTenant(tenantId, request.branchId());
        Integer categoryExists = jdbc.queryForObject("""
                SELECT COUNT(*) FROM menu_categories
                WHERE tenant_id = ? AND id = ? AND is_active = TRUE
                """, Integer.class, tenantId, request.categoryId());
        if (categoryExists == null || categoryExists == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Menu category not found.");
        }

        String dietaryType = request.dietaryType().trim().toUpperCase();
        if (!List.of("VEG", "NON_VEG", "EGG").contains(dietaryType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Dietary type must be VEG, NON_VEG or EGG.");
        }
        UUID itemId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO menu_items
                    (id, tenant_id, category_id, name, description, base_price, dietary_type, prep_time_minutes)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, itemId, tenantId, request.categoryId(), request.name().trim(),
                request.description(), request.price(), dietaryType, request.prepTimeMinutes());
        jdbc.update("""
                INSERT INTO branch_menu_items (tenant_id, branch_id, menu_item_id)
                VALUES (?, ?, ?)
                """, tenantId, request.branchId(), itemId);
        return ApiResponse.success("Menu item created",
                new ItemCreated(itemId, request.categoryId(), request.name().trim(), request.price(), dietaryType));
    }

    @PatchMapping("/items/{itemId}")
    @Transactional
    public ApiResponse<ItemUpdated> updateBranchItem(@PathVariable UUID tenantId,
                                                      @PathVariable UUID itemId,
                                                      @Valid @RequestBody UpdateMenuItemRequest request,
                                                      Authentication authentication) {
        access.requirePermission(authentication, tenantId, request.branchId(), "menu:manage");
        access.requireBranchInTenant(tenantId, request.branchId());
        int updated = jdbc.update("""
                UPDATE branch_menu_items
                SET price_override = ?, is_available = ?, updated_at = NOW()
                WHERE tenant_id = ? AND branch_id = ? AND menu_item_id = ?
                """, request.price(), request.available(), tenantId, request.branchId(), itemId);
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Menu item is not assigned to this branch.");
        }
        return ApiResponse.success("Menu item updated",
                new ItemUpdated(itemId, request.price(), request.available()));
    }

    public record CreateCategoryRequest(@NotNull UUID branchId,
                                        @NotBlank @Size(max=100) String name,
                                        @Size(max=300) String description,
                                        int sortOrder) {}
    public record CreateMenuItemRequest(@NotNull UUID branchId,
                                        @NotNull UUID categoryId,
                                        @NotBlank @Size(max=150) String name,
                                        @Size(max=500) String description,
                                        @NotNull @DecimalMin("0.01") BigDecimal price,
                                        @NotBlank String dietaryType,
                                        @NotNull @jakarta.validation.constraints.Min(1)
                                        @jakarta.validation.constraints.Max(240) Integer prepTimeMinutes) {}
    public record UpdateMenuItemRequest(@NotNull UUID branchId,
                                        @NotNull @DecimalMin("0.01") BigDecimal price,
                                        boolean available) {}
    public record MenuCategory(UUID id, String name, String description, int sortOrder,
                               boolean active, List<MenuItem> items) {}
    public record MenuItem(UUID id, UUID categoryId, String name, String description,
                           BigDecimal price, String dietaryType, int prepTimeMinutes,
                           boolean available) {}
    public record CategoryCreated(UUID id, String name) {}
    public record ItemCreated(UUID id, UUID categoryId, String name, BigDecimal price, String dietaryType) {}
    public record ItemUpdated(UUID id, BigDecimal price, boolean available) {}
}
