package com.restaurantos.modules.staff.repository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.*;
@Repository
public class PlatformServiceModeDao {
 private final JdbcTemplate jdbc;
 public PlatformServiceModeDao(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public List<Map<String,Object>> list(UUID tenantId){
  return jdbc.queryForList("""
   SELECT b.id AS "branchId",b.name AS "branchName",
    COALESCE(s.service_mode,'WAITER_CONFIRMED') AS "mode"
   FROM branches b LEFT JOIN restaurant_branch_service_settings s
    ON s.tenant_id=b.tenant_id AND s.branch_id=b.id
   WHERE b.tenant_id=? AND b.status='ACTIVE' ORDER BY b.name
   """,tenantId);
 }
 public boolean activeBranchExists(UUID tenantId,UUID branchId){
  Integer count=jdbc.queryForObject("SELECT COUNT(*) FROM branches WHERE tenant_id=? AND id=? AND status='ACTIVE'",Integer.class,tenantId,branchId);
  return count!=null&&count>0;
 }
 public void upsert(UUID tenantId,UUID branchId,String mode){
  jdbc.update("""
   INSERT INTO restaurant_branch_service_settings(tenant_id,branch_id,service_mode)
   VALUES(?,?,?) ON CONFLICT (tenant_id,branch_id) DO UPDATE SET service_mode=EXCLUDED.service_mode
   """,tenantId,branchId,mode);
 }
}
