package com.restaurantos.modules.platform.dto;
import com.restaurantos.common.api.ApiResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;


public final class PlatformAnalyticsDto {
 private PlatformAnalyticsDto(){}
    public record Day(LocalDate date, long orders, BigDecimal grossOrderValue) {}
    public record Overview(long restaurants, long activeRestaurants, long branches,
                           long ordersLast30Days, BigDecimal grossOrderValueLast30Days,
                           List<Day> dailyOrders) {}


}
