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
   FROM (SELECT * FROM restaurant_orders WHERE tenant_id=? AND branch_id=?
         ORDER BY created_at DESC,id DESC LIMIT 100) o
   JOIN restaurant_tables rt ON rt.tenant_id=o.tenant_id AND rt.id=o.table_id
   LEFT JOIN restaurant_order_items oi ON oi.tenant_id=o.tenant_id AND oi.order_id=o.id
   GROUP BY o.id,o.order_number,o.status,o.total_amount,o.created_at,rt.display_name
   ORDER BY o.created_at DESC,o.id DESC
   """,(rs,row)->new OrderSummary(rs.getObject("id",UUID.class),rs.getString("order_number"),
    rs.getString("table_name"),rs.getString("status"),rs.getBigDecimal("total_amount"),
    rs.getInt("line_count"),rs.getInt("item_count"),rs.getString("items_summary"),
    rs.getTimestamp("created_at").toInstant()),tenantId,branchId);
 }
 public List<OrderSummary> listLive(UUID tenantId,UUID branchId){
  return jdbc.query("""
   SELECT o.id,o.order_number,o.status,o.total_amount,o.created_at,
          rt.display_name AS table_name,COUNT(oi.id) AS line_count,
          COALESCE(SUM(oi.quantity),0) AS item_count,
          COALESCE(string_agg(oi.item_name || ' × ' || oi.quantity,', ' ORDER BY oi.created_at),'') AS items_summary
   FROM restaurant_orders o
   JOIN restaurant_tables rt ON rt.tenant_id=o.tenant_id AND rt.id=o.table_id
   LEFT JOIN restaurant_order_items oi ON oi.tenant_id=o.tenant_id AND oi.order_id=o.id
   WHERE o.tenant_id=? AND o.branch_id=? AND (
     o.status IN ('NEW','ACCEPTED','PREPARING','READY')
     OR o.id IN (SELECT recent.id FROM restaurant_orders recent
       WHERE recent.tenant_id=? AND recent.branch_id=? ORDER BY recent.created_at DESC,recent.id DESC LIMIT 100))
   GROUP BY o.id,o.order_number,o.status,o.total_amount,o.created_at,rt.display_name
   ORDER BY o.created_at DESC,o.id DESC
   """,(rs,row)->new OrderSummary(rs.getObject("id",UUID.class),rs.getString("order_number"),
    rs.getString("table_name"),rs.getString("status"),rs.getBigDecimal("total_amount"),
    rs.getInt("line_count"),rs.getInt("item_count"),rs.getString("items_summary"),
    rs.getTimestamp("created_at").toInstant()),tenantId,branchId,tenantId,branchId);
 }
 public List<OrderSummary> findById(UUID tenantId,UUID branchId,UUID orderId){
  return jdbc.query("""
   SELECT o.id,o.order_number,o.status,o.total_amount,o.created_at,
          rt.display_name AS table_name,COUNT(oi.id) AS line_count,
          COALESCE(SUM(oi.quantity),0) AS item_count,
          COALESCE(string_agg(oi.item_name || ' × ' || oi.quantity,', ' ORDER BY oi.created_at),'') AS items_summary
   FROM restaurant_orders o
   JOIN restaurant_tables rt ON rt.tenant_id=o.tenant_id AND rt.id=o.table_id
   LEFT JOIN restaurant_order_items oi ON oi.tenant_id=o.tenant_id AND oi.order_id=o.id
   WHERE o.tenant_id=? AND o.branch_id=? AND o.id=?
   GROUP BY o.id,rt.display_name ORDER BY o.created_at DESC LIMIT 1
   """,(rs,row)->new OrderSummary(rs.getObject("id",UUID.class),rs.getString("order_number"),
    rs.getString("table_name"),rs.getString("status"),rs.getBigDecimal("total_amount"),
    rs.getInt("line_count"),rs.getInt("item_count"),rs.getString("items_summary"),
    rs.getTimestamp("created_at").toInstant()),tenantId,branchId,orderId);
 }
 public int updateStatus(UUID tenantId,UUID branchId,UUID orderId,String status){
  return jdbc.update("""
   UPDATE restaurant_orders SET status=?,updated_at=NOW()
   WHERE tenant_id=? AND branch_id=? AND id=? AND status=?
   """,status,tenantId,branchId,orderId,switch(status){case "ACCEPTED"->"NEW";case "PREPARING"->"ACCEPTED";case "READY"->"PREPARING";case "SERVED"->"READY";case "CANCELLED"->"NEW";default->"__INVALID__";});
 }
 public void recordServed(UUID tenantId,UUID branchId,UUID orderId,UUID actorId){
  jdbc.update("""
   INSERT INTO restaurant_order_service_events(tenant_id,branch_id,order_id,actor_id,event_type)
   VALUES(?,?,?,?,?)
   """,tenantId,branchId,orderId,actorId,"WAITER_CONFIRMED_SERVED");
 }
}
