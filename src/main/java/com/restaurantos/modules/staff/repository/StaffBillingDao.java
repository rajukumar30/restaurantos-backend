package com.restaurantos.modules.staff.repository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.util.*;
@Repository
public class StaffBillingDao {
 private final JdbcTemplate jdbc;
 public StaffBillingDao(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public boolean cashier(UUID tenantId,UUID branchId,UUID userId){
  Integer count=jdbc.queryForObject("""
   SELECT COUNT(*) FROM tenant_users tu
   JOIN user_roles ur ON ur.tenant_id=tu.tenant_id AND ur.tenant_user_id=tu.id
   JOIN roles r ON r.id=ur.role_id
   WHERE tu.tenant_id=? AND tu.user_id=? AND tu.status='ACTIVE'
    AND (ur.branch_id IS NULL OR ur.branch_id=?)
    AND r.role_key IN ('CASHIER','MANAGER','OWNER')
   """,Integer.class,tenantId,userId,branchId);
  return count!=null&&count>0;
 }
 public List<Map<String,Object>> bills(UUID tenantId,UUID branchId){
  return jdbc.queryForList("""
   SELECT o.id,o.table_visit_id AS "visitId",o.order_number AS "orderNumber",rt.display_name AS "tableName",
   o.status,o.total_amount AS "totalAmount",
   COALESCE((SELECT SUM(p.amount) FROM restaurant_order_payments p
    WHERE p.tenant_id=o.tenant_id AND p.branch_id=o.branch_id AND p.order_id=o.id),0) AS "paidAmount",
   o.created_at AS "createdAt"
   FROM restaurant_orders o
   JOIN restaurant_tables rt ON rt.id=o.table_id AND rt.tenant_id=o.tenant_id
   WHERE o.tenant_id=? AND o.branch_id=? AND o.status IN ('SERVED','READY')
   ORDER BY o.created_at DESC LIMIT 100
   """,tenantId,branchId);
 }
 public List<BigDecimal> lockServedOrder(UUID tenantId,UUID branchId,UUID orderId){
  return jdbc.query("""
   SELECT total_amount FROM restaurant_orders
   WHERE tenant_id=? AND branch_id=? AND id=? AND status='SERVED' FOR UPDATE
   """,(rs,i)->rs.getBigDecimal(1),tenantId,branchId,orderId);
 }
 public BigDecimal paid(UUID tenantId,UUID branchId,UUID orderId){
  return jdbc.queryForObject("""
   SELECT COALESCE(SUM(amount),0) FROM restaurant_order_payments
   WHERE tenant_id=? AND branch_id=? AND order_id=?
   """,BigDecimal.class,tenantId,branchId,orderId);
 }
 public void insertPayment(UUID paymentId,UUID tenantId,UUID branchId,UUID orderId,
  BigDecimal amount,String method,String reference,UUID actor){
  jdbc.update("""
   INSERT INTO restaurant_order_payments(id,tenant_id,branch_id,order_id,amount,method,reference,recorded_by)
   VALUES(?,?,?,?,?,?,?,?)
   """,paymentId,tenantId,branchId,orderId,amount,method,reference,actor);
 }
 public void lockPaymentTable(UUID tenant,UUID branch,UUID order){
  jdbc.query("""
   SELECT rt.id FROM restaurant_tables rt JOIN restaurant_orders o ON o.table_id=rt.id AND o.tenant_id=rt.tenant_id AND o.branch_id=rt.branch_id
   WHERE o.tenant_id=? AND o.branch_id=? AND o.id=? FOR UPDATE OF rt
   """,(rs,row)->rs.getObject(1,UUID.class),tenant,branch,order);
 }
 public int settleVisit(UUID tenant,UUID branch,UUID order){
  return jdbc.update("""
   UPDATE table_visits v SET status='PAID',floor_paid_at=NOW(),floor_grace_until=NOW()+INTERVAL '5 minutes'
   WHERE v.tenant_id=? AND v.branch_id=? AND v.status='OPEN'
    AND v.id=(SELECT table_visit_id FROM restaurant_orders WHERE tenant_id=v.tenant_id AND branch_id=v.branch_id AND id=?)
    AND EXISTS(SELECT 1 FROM restaurant_orders o WHERE o.table_visit_id=v.id AND o.tenant_id=v.tenant_id AND o.branch_id=v.branch_id AND o.status='SERVED')
    AND NOT EXISTS(SELECT 1 FROM restaurant_orders o WHERE o.table_visit_id=v.id AND o.tenant_id=v.tenant_id AND o.branch_id=v.branch_id AND o.status<>'CANCELLED'
      AND (o.status<>'SERVED' OR o.total_amount>COALESCE((SELECT SUM(p.amount) FROM restaurant_order_payments p WHERE p.tenant_id=o.tenant_id AND p.branch_id=o.branch_id AND p.order_id=o.id),0)))
   """,tenant,branch,order);
 }
}
