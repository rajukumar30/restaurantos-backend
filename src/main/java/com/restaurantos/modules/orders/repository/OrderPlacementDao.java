package com.restaurantos.modules.orders.repository;
import com.restaurantos.modules.orders.entity.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.*;
import java.math.BigDecimal;
@Repository
public class OrderPlacementDao {
 private final JdbcTemplate jdbc;
 public OrderPlacementDao(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public List<QrTable> findActiveTable(String qrToken){
  return jdbc.query("""
   SELECT rt.id AS table_id,rt.tenant_id,rt.branch_id,rt.display_name
   FROM restaurant_tables rt
   JOIN branches b ON b.id=rt.branch_id AND b.tenant_id=rt.tenant_id
   JOIN tenants t ON t.id=rt.tenant_id
   WHERE rt.qr_token=? AND rt.is_active=TRUE AND b.status='ACTIVE' AND t.status='ACTIVE'
   """,(rs,row)->new QrTable(rs.getObject("table_id",UUID.class),rs.getObject("tenant_id",UUID.class),
    rs.getObject("branch_id",UUID.class),rs.getString("display_name")),qrToken);
 }
 public List<ValidatedOrderLine> findAvailableItem(UUID tenantId,UUID branchId,UUID itemId,int quantity){
  return jdbc.query("""
   SELECT mi.id,mi.name,COALESCE(bmi.price_override,mi.base_price) AS price
   FROM menu_items mi
   JOIN menu_categories mc ON mc.tenant_id=mi.tenant_id AND mc.id=mi.category_id
   JOIN branch_menu_items bmi ON bmi.tenant_id=mi.tenant_id AND bmi.menu_item_id=mi.id
   WHERE mi.tenant_id=? AND mi.id=? AND bmi.branch_id=?
    AND mi.is_active=TRUE AND mc.is_active=TRUE AND bmi.is_available=TRUE
   """,(rs,row)->new ValidatedOrderLine(rs.getObject("id",UUID.class),
    rs.getString("name"),rs.getBigDecimal("price"),quantity),tenantId,itemId,branchId);
 }
 public void lockTable(UUID tableId){
  jdbc.queryForObject("SELECT id FROM restaurant_tables WHERE id=? FOR UPDATE",UUID.class,tableId);
 }
 public List<UUID> activeVisits(QrTable table){
  return jdbc.query("""
   SELECT id FROM table_visits
   WHERE tenant_id=? AND branch_id=? AND table_id=?
    AND status IN ('OPEN','PAYMENT_PENDING','PAID') FOR UPDATE
   """,(rs,row)->rs.getObject(1,UUID.class),table.tenantId(),table.branchId(),table.tableId());
 }
 public String visitStatus(UUID visitId){
  return jdbc.queryForObject("SELECT status FROM table_visits WHERE id=?",String.class,visitId);
 }
 public void createVisit(UUID visitId,QrTable table,String billNumber){
  jdbc.update("""
   INSERT INTO table_visits(id,tenant_id,branch_id,table_id,bill_number,status)
   VALUES(?,?,?,?,?,'OPEN')
   """,visitId,table.tenantId(),table.branchId(),table.tableId(),billNumber);
 }
 public void createOrder(UUID orderId,String orderNumber,QrTable table,UUID visitId,
                         String name,String phone,String notes,BigDecimal total){
  jdbc.update("""
   INSERT INTO restaurant_orders
    (id,tenant_id,branch_id,table_id,order_number,customer_name,notes,total_amount,customer_phone,table_visit_id)
   VALUES(?,?,?,?,?,?,?,?,?,?)
   """,orderId,table.tenantId(),table.branchId(),table.tableId(),orderNumber,name,notes,total,phone,visitId);
 }
 public void createOrderItem(UUID tenantId,UUID orderId,ValidatedOrderLine line){
  jdbc.update("""
   INSERT INTO restaurant_order_items
    (tenant_id,order_id,menu_item_id,item_name,quantity,unit_price,line_total)
   VALUES(?,?,?,?,?,?,?)
   """,tenantId,orderId,line.menuItemId(),line.itemName(),line.quantity(),
   line.unitPrice(),line.unitPrice().multiply(BigDecimal.valueOf(line.quantity())));
 }
}
