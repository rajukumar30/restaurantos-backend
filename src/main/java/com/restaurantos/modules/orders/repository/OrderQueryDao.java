package com.restaurantos.modules.orders.repository;
import com.restaurantos.modules.orders.dto.OrderDto.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.*;
@Repository
public class OrderQueryDao {
 private final JdbcTemplate jdbc;
 public OrderQueryDao(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public List<PublicOrderStatus> publicStatus(String qrToken,UUID orderId){
  return jdbc.query("""
   SELECT o.order_number,o.status,o.total_amount,o.created_at
   FROM restaurant_orders o
   JOIN restaurant_tables rt ON rt.id=o.table_id AND rt.tenant_id=o.tenant_id
   JOIN branches b ON b.id=o.branch_id AND b.tenant_id=o.tenant_id
   JOIN tenants t ON t.id=o.tenant_id
   WHERE o.id=? AND rt.qr_token=? AND rt.is_active=TRUE
     AND b.status='ACTIVE' AND t.status='ACTIVE'
   """,(rs,row)->new PublicOrderStatus(rs.getString("order_number"),rs.getString("status"),
    rs.getBigDecimal("total_amount"),rs.getTimestamp("created_at").toInstant()),orderId,qrToken);
 }
 public List<OrderSummary> list(UUID tenantId,UUID branchId){
  return jdbc.query("""
   SELECT o.id,o.order_number,o.status,o.total_amount,o.created_at,
          rt.display_name AS table_name,COUNT(oi.id) AS line_count,
          COALESCE(SUM(oi.quantity),0) AS item_count,
          COALESCE(string_agg(oi.item_name || ' × ' || oi.quantity,', ' ORDER BY oi.created_at),'') AS items_summary
   FROM restaurant_orders o
   JOIN restaurant_tables rt ON rt.tenant_id=o.tenant_id AND rt.id=o.table_id
   LEFT JOIN restaurant_order_items oi ON oi.tenant_id=o.tenant_id AND oi.order_id=o.id
   WHERE o.tenant_id=? AND o.branch_id=?
   GROUP BY o.id,rt.display_name ORDER BY o.created_at DESC LIMIT 100
   """,(rs,row)->new OrderSummary(rs.getObject("id",UUID.class),rs.getString("order_number"),
    rs.getString("table_name"),rs.getString("status"),rs.getBigDecimal("total_amount"),
    rs.getInt("line_count"),rs.getInt("item_count"),rs.getString("items_summary"),
    rs.getTimestamp("created_at").toInstant()),tenantId,branchId);
 }
 public int updateStatus(UUID tenantId,UUID branchId,UUID orderId,String status){
  return jdbc.update("""
   UPDATE restaurant_orders SET status=?,updated_at=NOW()
   WHERE tenant_id=? AND branch_id=? AND id=? AND status=?
   """,status,tenantId,branchId,orderId,status.equals("SERVED")?"READY":"NEW");
 }
 public void recordServed(UUID tenantId,UUID branchId,UUID orderId,UUID actorId){
  jdbc.update("""
   INSERT INTO restaurant_order_service_events(tenant_id,branch_id,order_id,actor_id,event_type)
   VALUES(?,?,?,?,?)
   """,tenantId,branchId,orderId,actorId,"WAITER_CONFIRMED_SERVED");
 }
}
