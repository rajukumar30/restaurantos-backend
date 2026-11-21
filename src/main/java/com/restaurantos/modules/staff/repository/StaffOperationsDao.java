package com.restaurantos.modules.staff.repository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.*;
@Repository
public class StaffOperationsDao {
 private final JdbcTemplate jdbc;
 public StaffOperationsDao(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public List<Map<String,Object>> branches(UUID userId,UUID tenantId){
  return jdbc.queryForList("""
   SELECT DISTINCT b.id,b.name FROM branches b
   JOIN tenant_users tu ON tu.tenant_id=b.tenant_id AND tu.user_id=? AND tu.status='ACTIVE'
   JOIN user_roles ur ON ur.tenant_id=tu.tenant_id AND ur.tenant_user_id=tu.id
    AND (ur.branch_id IS NULL OR ur.branch_id=b.id)
   JOIN role_permissions rp ON rp.role_id=ur.role_id
   JOIN permissions p ON p.id=rp.permission_id AND p.permission_key='orders:read'
   WHERE b.tenant_id=? AND b.status='ACTIVE' ORDER BY b.name
   """,userId,tenantId);
 }
 public List<String> roles(UUID tenantId,UUID userId,UUID branchId){
  return jdbc.query("""
   SELECT DISTINCT r.role_key FROM tenant_users tu
   JOIN user_roles ur ON ur.tenant_user_id=tu.id AND ur.tenant_id=tu.tenant_id
   JOIN roles r ON r.id=ur.role_id
   WHERE tu.tenant_id=? AND tu.user_id=? AND tu.status='ACTIVE'
    AND (ur.branch_id IS NULL OR ur.branch_id=?)
   """,(rs,i)->rs.getString(1),tenantId,userId,branchId);
 }
 public List<String> permissions(UUID tenantId,UUID userId,UUID branchId){
  return jdbc.query("""
   SELECT DISTINCT p.permission_key FROM tenant_users tu
   JOIN user_roles ur ON ur.tenant_user_id=tu.id AND ur.tenant_id=tu.tenant_id
   JOIN role_permissions rp ON rp.role_id=ur.role_id
   JOIN permissions p ON p.id=rp.permission_id
   WHERE tu.tenant_id=? AND tu.user_id=? AND tu.status='ACTIVE'
    AND (ur.branch_id IS NULL OR ur.branch_id=?)
   """,(rs,i)->rs.getString(1),tenantId,userId,branchId);
 }
 public String mode(UUID tenantId,UUID branchId){
  return jdbc.queryForObject("SELECT COALESCE((SELECT service_mode FROM restaurant_branch_service_settings WHERE tenant_id=? AND branch_id=?),'WAITER_CONFIRMED')",String.class,tenantId,branchId);
 }
 public int updateStatus(UUID tenantId,UUID branchId,UUID orderId,String target,String from){
  return jdbc.update("""
   UPDATE restaurant_orders SET status=?,updated_at=NOW()
   WHERE tenant_id=? AND branch_id=? AND id=? AND status=?
   """,target,tenantId,branchId,orderId,from);
 }
 public void recordKitchenHandoff(UUID tenantId,UUID branchId,UUID orderId,UUID actorId){
  jdbc.update("""
   INSERT INTO restaurant_order_service_events(tenant_id,branch_id,order_id,actor_id,event_type)
   VALUES(?,?,?,?,'KITCHEN_HANDOFF_SERVED')
   """,tenantId,branchId,orderId,actorId);
 }
}
