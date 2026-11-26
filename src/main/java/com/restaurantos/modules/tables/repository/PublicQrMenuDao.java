package com.restaurantos.modules.tables.repository;
import com.restaurantos.modules.tables.dto.PublicMenuDto.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurantos.modules.branding.repository.BrandingDao;
import com.restaurantos.modules.branding.dto.BrandingSettings;
@Repository
public class PublicQrMenuDao {
 private final JdbcTemplate jdbc;
 private final ObjectMapper mapper;
 public PublicQrMenuDao(JdbcTemplate jdbc,ObjectMapper mapper){this.jdbc=jdbc;this.mapper=mapper;}
 private record ResolvedTable(UUID tenantId,PublicMenu menu){}
 public PublicMenu resolve(String qrToken){
        List<ResolvedTable> matches = jdbc.query("""
                SELECT rt.id AS table_id, rt.display_name AS table_name,
                       b.id AS branch_id, b.name AS branch_name, t.name AS restaurant_name,
                       rt.tenant_id, %s
                FROM restaurant_tables rt
                JOIN branches b ON b.id = rt.branch_id AND b.tenant_id = rt.tenant_id
                JOIN tenants t ON t.id = rt.tenant_id
                LEFT JOIN restaurant_guest_branding gb ON gb.tenant_id = rt.tenant_id
                WHERE rt.qr_token = ? AND rt.is_active = TRUE
                  AND b.status = 'ACTIVE' AND t.status = 'ACTIVE'
                """.formatted(BrandingDao.COLUMNS), (rs, row) -> {
                BrandingSettings branding=BrandingDao.read(rs,mapper,rs.getString("restaurant_name"));
                return new ResolvedTable(rs.getObject("tenant_id",UUID.class),new PublicMenu(
                rs.getObject("table_id", UUID.class), rs.getString("table_name"),
                rs.getObject("branch_id", UUID.class), rs.getString("branch_name"),
                branding.displayName(), List.of(), branding.slogan(), branding.logoUrl(), branding.bannerUrl(), branding.theme(),branding));}, qrToken);
        if (matches.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "This table QR code is invalid or inactive.");
        }
        PublicMenu table = matches.getFirst().menu();
        UUID tenantId = matches.getFirst().tenantId();
        Map<UUID, List<PublicItem>> grouped = new LinkedHashMap<>();
        jdbc.query("""
                SELECT mc.id AS category_id, mc.name AS category_name,
                       mi.id AS item_id, mi.name AS item_name, mi.description,
                       COALESCE(bmi.price_override, mi.base_price) AS price,
                       mi.dietary_type, mi.prep_time_minutes, '/api/v1/public/menu/images/' || CAST(img.id AS text) AS image_url
                FROM menu_categories mc
                JOIN menu_items mi ON mi.tenant_id = mc.tenant_id AND mi.category_id = mc.id
                JOIN branch_menu_items bmi ON bmi.tenant_id = mi.tenant_id AND bmi.menu_item_id = mi.id
                LEFT JOIN menu_item_images img ON img.tenant_id=mi.tenant_id AND img.item_id=mi.id
                WHERE mc.tenant_id = ? AND bmi.branch_id = ?
                  AND mc.is_active = TRUE AND mi.is_active = TRUE AND bmi.is_available = TRUE
                ORDER BY mc.sort_order, mc.name, mi.name
                """, rs -> {
            UUID categoryId = rs.getObject("category_id", UUID.class);
            grouped.computeIfAbsent(categoryId, ignored -> new ArrayList<>()).add(new PublicItem(
                    rs.getObject("item_id", UUID.class), rs.getString("item_name"),
                    rs.getString("description"), rs.getBigDecimal("price"),
                    rs.getString("dietary_type"), rs.getInt("prep_time_minutes"),rs.getString("image_url")));
        }, tenantId, table.branchId());
        List<PublicCategory> categories = jdbc.query("""
                SELECT id, name FROM menu_categories
                WHERE tenant_id = ? AND is_active = TRUE ORDER BY sort_order, name
                """, (rs, row) -> {
            UUID id = rs.getObject("id", UUID.class);
            return new PublicCategory(id, rs.getString("name"), grouped.getOrDefault(id, List.of()));
        }, tenantId);
        return new PublicMenu(
                table.tableId(), table.tableName(), table.branchId(), table.branchName(),
                table.restaurantName(), categories, table.slogan(), table.logoUrl(), table.bannerUrl(), table.theme(),table.branding());
    }

}
