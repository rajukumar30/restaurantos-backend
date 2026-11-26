package com.restaurantos.modules.tables.repository;
import com.restaurantos.modules.tables.dto.PublicRestaurantDto.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurantos.modules.branding.repository.BrandingDao;
import com.restaurantos.modules.branding.dto.BrandingSettings;
@Repository
public class PublicRestaurantMenuDao {
 private final JdbcTemplate jdbc;
 private final ObjectMapper mapper;
 public PublicRestaurantMenuDao(JdbcTemplate jdbc,ObjectMapper mapper){this.jdbc=jdbc;this.mapper=mapper;}
 private record Restaurant(UUID id,String slug,BrandingSettings branding){}
 public RestaurantMenu getMenu(String slug,UUID branchId){
        List<Restaurant> restaurants = jdbc.query("""
                SELECT t.id, t.name, t.slug, %s
                FROM tenants t LEFT JOIN restaurant_guest_branding gb ON gb.tenant_id=t.id
                WHERE t.slug = ? AND t.status = 'ACTIVE'
                """.formatted(BrandingDao.COLUMNS), (rs, row) -> new Restaurant(
                rs.getObject("id", UUID.class), rs.getString("slug"), BrandingDao.read(rs,mapper,rs.getString("name"))), slug);
        if (restaurants.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Restaurant not found or inactive.");
        }
        Restaurant restaurant = restaurants.getFirst();

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

        return new RestaurantMenu(restaurant.branding().displayName(), restaurant.slug(), branches, selectedBranch.id(),
                        selectedBranch.name(), categories,restaurant.branding());
    }

}
