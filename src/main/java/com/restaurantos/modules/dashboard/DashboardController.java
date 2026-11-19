package com.restaurantos.modules.dashboard;

import com.restaurantos.common.api.ApiResponse;
import com.restaurantos.modules.staff.TenantAccessService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/dashboard")
public class DashboardController {
    private final JdbcTemplate jdbc;
    private final TenantAccessService access;

    public DashboardController(JdbcTemplate jdbc, TenantAccessService access) {
        this.jdbc = jdbc;
        this.access = access;
    }

    @GetMapping
    public ApiResponse<DashboardData> dashboard(@PathVariable UUID tenantId,
                                                 @RequestParam UUID branchId,
                                                 Authentication authentication) {
        access.requirePermission(authentication, tenantId, branchId, "orders:read");
        access.requireBranchInTenant(tenantId, branchId);

        var summary = jdbc.queryForObject("""
            SELECT COUNT(*) FILTER (WHERE (o.created_at AT TIME ZONE b.timezone)::date =
                       (NOW() AT TIME ZONE b.timezone)::date) AS orders_today,
                   COALESCE(SUM(o.total_amount) FILTER (WHERE o.status <> 'CANCELLED'
                       AND (o.created_at AT TIME ZONE b.timezone)::date =
                       (NOW() AT TIME ZONE b.timezone)::date), 0) AS revenue_today,
                   COUNT(*) FILTER (WHERE o.status IN ('NEW','ACCEPTED','PREPARING')) AS active_orders,
                   COUNT(*) FILTER (WHERE o.status = 'READY') AS ready_orders
            FROM branches b
            LEFT JOIN restaurant_orders o ON o.tenant_id=b.tenant_id AND o.branch_id=b.id
            WHERE b.tenant_id=? AND b.id=?
            GROUP BY b.id, b.timezone
            """, (rs, i) -> new Summary(rs.getLong("orders_today"),
                    rs.getBigDecimal("revenue_today"), rs.getLong("active_orders"),
                    rs.getLong("ready_orders")), tenantId, branchId);

        var sales = jdbc.query("""
            SELECT d.day::date AS day, COALESCE(SUM(o.total_amount), 0) AS revenue,
                   COUNT(o.id) AS orders
            FROM branches b
            CROSS JOIN LATERAL generate_series(
                (NOW() AT TIME ZONE b.timezone)::date - INTERVAL '6 days',
                (NOW() AT TIME ZONE b.timezone)::date, INTERVAL '1 day') d(day)
            LEFT JOIN restaurant_orders o ON o.tenant_id=b.tenant_id AND o.branch_id=b.id
                AND o.status <> 'CANCELLED'
                AND (o.created_at AT TIME ZONE b.timezone)::date=d.day::date
            WHERE b.tenant_id=? AND b.id=?
            GROUP BY d.day ORDER BY d.day
            """, (rs, i) -> new DailySales(rs.getDate("day").toLocalDate().toString(),
                    rs.getBigDecimal("revenue"), rs.getLong("orders")), tenantId, branchId);

        var popular = jdbc.query("""
            SELECT oi.item_name, SUM(oi.quantity) AS quantity,
                   SUM(oi.line_total) AS revenue
            FROM restaurant_order_items oi
            JOIN restaurant_orders o ON o.id=oi.order_id AND o.tenant_id=oi.tenant_id
            WHERE o.tenant_id=? AND o.branch_id=? AND o.status <> 'CANCELLED'
              AND o.created_at >= NOW() - INTERVAL '30 days'
            GROUP BY oi.item_name ORDER BY quantity DESC, revenue DESC LIMIT 5
            """, (rs, i) -> new PopularItem(rs.getString("item_name"),
                    rs.getLong("quantity"), rs.getBigDecimal("revenue")), tenantId, branchId);

        var recent = jdbc.query("""
            SELECT o.order_number, o.status, o.total_amount, rt.display_name AS table_name
            FROM restaurant_orders o
            JOIN restaurant_tables rt ON rt.tenant_id=o.tenant_id AND rt.id=o.table_id
            WHERE o.tenant_id=? AND o.branch_id=?
            ORDER BY o.created_at DESC LIMIT 8
            """, (rs, i) -> new RecentOrder(rs.getString("order_number"),
                    rs.getString("status"), rs.getBigDecimal("total_amount"),
                    rs.getString("table_name")), tenantId, branchId);

        return ApiResponse.success("Dashboard loaded", new DashboardData(summary, sales, popular, recent));
    }

    public record Summary(long ordersToday, BigDecimal revenueToday,
                          long activeOrders, long readyOrders) {}
    public record DailySales(String day, BigDecimal revenue, long orders) {}
    public record PopularItem(String name, long quantity, BigDecimal revenue) {}
    public record RecentOrder(String orderNumber, String status, BigDecimal totalAmount,
                              String tableName) {}
    public record DashboardData(Summary summary, List<DailySales> dailySales,
                                List<PopularItem> popularItems, List<RecentOrder> recentOrders) {}
}
