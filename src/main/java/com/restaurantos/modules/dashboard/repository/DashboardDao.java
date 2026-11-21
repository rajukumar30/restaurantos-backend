package com.restaurantos.modules.dashboard.repository;
import com.restaurantos.modules.dashboard.dto.DashboardData;
import com.restaurantos.modules.dashboard.dto.DashboardData.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.UUID;
@Repository
public class DashboardDao {
 private final JdbcTemplate jdbc;
 public DashboardDao(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public DashboardData fetch(UUID tenantId,UUID branchId){
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

  return new DashboardData(summary,sales,popular,recent);
 }
}
