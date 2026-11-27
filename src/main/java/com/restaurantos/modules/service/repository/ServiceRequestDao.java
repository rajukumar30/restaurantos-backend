package com.restaurantos.modules.service.repository;
import com.restaurantos.modules.service.dto.ServiceRequestDto.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.*;
@Repository
public class ServiceRequestDao {
 private final JdbcTemplate jdbc;
 public ServiceRequestDao(JdbcTemplate jdbc){this.jdbc=jdbc;}
 private static ServiceRequest map(java.sql.ResultSet rs,int i)throws java.sql.SQLException{
  return new ServiceRequest(rs.getObject(1,UUID.class),rs.getString(2),rs.getString(3),
   rs.getString(4),rs.getTimestamp(5).toInstant(),rs.getString(6));
 }
 public List<TableInfo> tables(String token){
  return jdbc.query("""
   SELECT rt.id,rt.tenant_id,rt.branch_id FROM restaurant_tables rt
   JOIN branches b ON b.id=rt.branch_id AND b.tenant_id=rt.tenant_id
   JOIN tenants t ON t.id=rt.tenant_id
   WHERE rt.qr_token=? AND rt.is_active=TRUE AND b.status='ACTIVE' AND t.status='ACTIVE'
   """,(rs,i)->new TableInfo(rs.getObject(1,UUID.class),rs.getObject(2,UUID.class),
    rs.getObject(3,UUID.class)),token);
 }
 public List<UUID> requestTable(UUID requestId,UUID tenantId,UUID branchId){
  return jdbc.query("SELECT table_id FROM service_requests WHERE id=? AND tenant_id=? AND branch_id=?",
   (rs,i)->rs.getObject(1,UUID.class),requestId,tenantId,branchId);
 }
 public void lockTable(UUID id){jdbc.queryForObject("SELECT id FROM restaurant_tables WHERE id=? FOR UPDATE",UUID.class,id);}
 public List<ServiceRequest> active(UUID tableId,String type){
  return jdbc.query("""
   SELECT sr.id,sr.request_type,sr.status,sr.notes,sr.created_at,rt.display_name
   FROM service_requests sr JOIN restaurant_tables rt ON rt.id=sr.table_id
   WHERE sr.table_id=? AND sr.request_type=? AND sr.status IN ('REQUESTED','ON_THE_WAY')
   ORDER BY sr.created_at DESC LIMIT 1
   """,ServiceRequestDao::map,tableId,type);
 }
 public void insert(UUID id,TableInfo table,CreateRequest request){
  jdbc.update("""
   INSERT INTO service_requests(id,tenant_id,branch_id,table_id,request_type,notes)
   VALUES(?,?,?,?,?,?)
   """,id,table.tenantId(),table.branchId(),table.id(),request.type(),
   request.notes()==null||request.notes().isBlank()?null:request.notes().trim());
 }
 public List<ServiceRequest> guestList(TableInfo table){
  return jdbc.query("""
   SELECT sr.id,sr.request_type,sr.status,sr.notes,sr.created_at,rt.display_name
   FROM service_requests sr JOIN restaurant_tables rt ON rt.id=sr.table_id
   WHERE sr.table_id=? AND sr.tenant_id=? AND sr.branch_id=?
    AND sr.status IN ('REQUESTED','ON_THE_WAY') ORDER BY sr.created_at DESC
   """,ServiceRequestDao::map,table.id(),table.tenantId(),table.branchId());
 }
 public int cancel(UUID id,TableInfo table){
  return jdbc.update("""
   UPDATE service_requests SET status='CANCELLED',cancelled_at=NOW()
   WHERE id=? AND table_id=? AND tenant_id=? AND branch_id=? AND status='REQUESTED'
   """,id,table.id(),table.tenantId(),table.branchId());
 }
 public List<ServiceRequest> staffList(UUID tenantId,UUID branchId){
  return jdbc.query("""
   SELECT sr.id,sr.request_type,sr.status,sr.notes,sr.created_at,rt.display_name
   FROM service_requests sr JOIN restaurant_tables rt ON rt.id=sr.table_id
   WHERE sr.tenant_id=? AND sr.branch_id=? AND sr.status IN ('REQUESTED','ON_THE_WAY')
   ORDER BY sr.created_at ASC
   """,ServiceRequestDao::map,tenantId,branchId);
 }
 public int update(UUID id,UUID tenantId,UUID branchId,String status,UUID staff){
  if("ON_THE_WAY".equals(status))
   return jdbc.update("""
    UPDATE service_requests SET status='ON_THE_WAY',accepted_at=NOW(),assigned_staff_id=?
    WHERE id=? AND tenant_id=? AND branch_id=? AND status='REQUESTED'
    """,staff,id,tenantId,branchId);
  return jdbc.update("""
   UPDATE service_requests SET status='COMPLETED',completed_at=NOW()
   WHERE id=? AND tenant_id=? AND branch_id=? AND status='ON_THE_WAY'
   """,id,tenantId,branchId);
 }
 public List<ServiceRequest> find(UUID id,UUID tenantId,UUID branchId){
  return jdbc.query("""
   SELECT sr.id,sr.request_type,sr.status,sr.notes,sr.created_at,rt.display_name
   FROM service_requests sr JOIN restaurant_tables rt ON rt.id=sr.table_id
   WHERE sr.id=? AND sr.tenant_id=? AND sr.branch_id=?
   """,ServiceRequestDao::map,id,tenantId,branchId);
 }
}
