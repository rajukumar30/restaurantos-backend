package com.restaurantos.modules.platform;

import com.restaurantos.modules.platform.dto.PlatformAnalyticsDto.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Repository
public class PlatformAnalyticsDao {
    private final JdbcTemplate jdbc;
    public PlatformAnalyticsDao(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public Overview overview() {
        var restaurants=jdbc.queryForMap("""
            SELECT COUNT(*) AS total,COUNT(*) FILTER (WHERE status='ACTIVE') AS active FROM tenants
            """);
        Long branches=jdbc.queryForObject("SELECT COUNT(*) FROM branches",Long.class);
        var totals=jdbc.queryForMap("""
            SELECT COUNT(*) AS orders,COALESCE(SUM(total_amount),0) AS gross
            FROM restaurant_orders WHERE created_at>=NOW()-INTERVAL '30 days'
            """);
        List<Day> days=jdbc.query("""
            WITH dates AS (
              SELECT generate_series((CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::date - 6,
                                     (CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::date,
                                     INTERVAL '1 day')::date AS day
            ), orders_by_day AS (
              SELECT (created_at AT TIME ZONE 'UTC')::date AS day,
                     COUNT(*) AS orders,COALESCE(SUM(total_amount),0) AS gross
              FROM restaurant_orders
              WHERE created_at>=((CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::date - 6) AT TIME ZONE 'UTC'
              GROUP BY 1
            )
            SELECT d.day,COALESCE(o.orders,0) AS orders,COALESCE(o.gross,0) AS gross
            FROM dates d LEFT JOIN orders_by_day o ON d.day=o.day ORDER BY d.day
            """,(rs,i)->new Day(rs.getObject("day",LocalDate.class),
                                 rs.getLong("orders"),rs.getBigDecimal("gross")));
        return new Overview(((Number)restaurants.get("total")).longValue(),
            ((Number)restaurants.get("active")).longValue(),
            branches==null?0:branches,
            ((Number)totals.get("orders")).longValue(),
            (BigDecimal)totals.get("gross"),days);
    }
}
