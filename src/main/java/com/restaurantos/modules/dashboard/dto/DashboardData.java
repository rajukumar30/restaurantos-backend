package com.restaurantos.modules.dashboard.dto;
import java.math.BigDecimal;
import java.util.List;
public record DashboardData(Summary summary,List<DailySales> dailySales,List<PopularItem> popularItems,List<RecentOrder> recentOrders) {
 public record Summary(long ordersToday,BigDecimal revenueToday,long activeOrders,long readyOrders) {}
 public record DailySales(String day,BigDecimal revenue,long orders) {}
 public record PopularItem(String name,long quantity,BigDecimal revenue) {}
 public record RecentOrder(String orderNumber,String status,BigDecimal totalAmount,String tableName) {}
}
