package com.restaurantos.modules.orders.repository;
import com.restaurantos.modules.orders.dto.TableBillDto.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.*;
@Repository
public class TableBillDao {
 private final JdbcTemplate jdbc;
 public TableBillDao(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public List<BillSummary> active(UUID tenantId,UUID branchId){
  return jdbc.query("""
   SELECT v.id,v.bill_number,v.status,rt.display_name,v.opened_at,
    COALESCE(SUM(o.total_amount) FILTER (WHERE o.status <> 'CANCELLED'),0) AS amount,
    COUNT(o.id) FILTER (WHERE o.status <> 'CANCELLED') AS order_count
   FROM table_visits v
   JOIN restaurant_tables rt ON rt.id=v.table_id AND rt.tenant_id=v.tenant_id
   LEFT JOIN restaurant_orders o ON o.table_visit_id=v.id AND o.tenant_id=v.tenant_id
   WHERE v.tenant_id=? AND v.branch_id=? AND v.status IN ('OPEN','PAYMENT_PENDING','PAID')
   GROUP BY v.id,rt.display_name ORDER BY v.opened_at DESC
   """,(rs,i)->new BillSummary(rs.getObject(1,UUID.class),rs.getString(2),
   rs.getString(3),rs.getString(4),rs.getTimestamp(5).toInstant(),
   rs.getBigDecimal(6),rs.getInt(7)),tenantId,branchId);
 }
 public List<BillSummary> summary(UUID tenantId,UUID branchId,UUID visitId){
  return jdbc.query("""
   SELECT v.id,v.bill_number,v.status,rt.display_name,v.opened_at,
    COALESCE(SUM(o.total_amount) FILTER (WHERE o.status <> 'CANCELLED'),0),
    COUNT(o.id) FILTER (WHERE o.status <> 'CANCELLED')
   FROM table_visits v
   JOIN restaurant_tables rt ON rt.id=v.table_id AND rt.tenant_id=v.tenant_id
   LEFT JOIN restaurant_orders o ON o.table_visit_id=v.id AND o.tenant_id=v.tenant_id
   WHERE v.id=? AND v.tenant_id=? AND v.branch_id=?
   GROUP BY v.id,rt.display_name
   """,(rs,i)->new BillSummary(rs.getObject(1,UUID.class),rs.getString(2),
   rs.getString(3),rs.getString(4),rs.getTimestamp(5).toInstant(),
   rs.getBigDecimal(6),rs.getInt(7)),visitId,tenantId,branchId);
 }
 public List<BillLine> lines(UUID tenantId,UUID branchId,UUID visitId){
  return jdbc.query("""
   SELECT o.order_number,oi.item_name,oi.quantity,oi.unit_price,oi.line_total
   FROM restaurant_orders o
   JOIN restaurant_order_items oi ON oi.order_id=o.id AND oi.tenant_id=o.tenant_id
   WHERE o.table_visit_id=? AND o.tenant_id=? AND o.branch_id=? AND o.status <> 'CANCELLED'
   ORDER BY o.created_at,oi.created_at
   """,(rs,i)->new BillLine(rs.getString(1),rs.getString(2),rs.getInt(3),
   rs.getBigDecimal(4),rs.getBigDecimal(5)),visitId,tenantId,branchId);
 }
}
