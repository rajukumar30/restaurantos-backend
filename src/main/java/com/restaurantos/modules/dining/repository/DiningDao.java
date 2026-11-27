package com.restaurantos.modules.dining.repository;
import com.restaurantos.modules.dining.dto.DiningDto.*;
import com.restaurantos.modules.orders.dto.CheckoutDto.Scope;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.*;
@Repository
public class DiningDao{
 private final JdbcTemplate jdbc;public DiningDao(JdbcTemplate jdbc){this.jdbc=jdbc;}
 private static final String TABLES="""
 FROM restaurant_tables t JOIN tenants n ON n.id=t.tenant_id JOIN branches b ON b.id=t.branch_id AND b.tenant_id=t.tenant_id
 WHERE t.is_active AND n.status='ACTIVE' AND b.status='ACTIVE'
 """;
 public boolean lockQr(String qr){return !jdbc.query("SELECT t.id "+TABLES+" AND t.qr_token=? FOR UPDATE OF t",(r,i)->r.getObject(1,UUID.class),qr).isEmpty();}
 public boolean lockTable(UUID tenant,UUID branch,UUID table){return !jdbc.query("SELECT t.id "+TABLES+" AND t.tenant_id=? AND t.branch_id=? AND t.id=? FOR UPDATE OF t",(r,i)->r.getObject(1,UUID.class),tenant,branch,table).isEmpty();}
 public Optional<Active> active(String qr){
  return jdbc.query("""
  SELECT v.tenant_id,v.branch_id,v.table_id,v.id,v.status,v.dining_code_hash,v.dining_code_expires_at,v.dining_code_failures
  FROM table_visits v JOIN restaurant_tables t ON t.id=v.table_id AND t.tenant_id=v.tenant_id AND t.branch_id=v.branch_id
  JOIN tenants n ON n.id=v.tenant_id JOIN branches b ON b.id=v.branch_id AND b.tenant_id=v.tenant_id
  WHERE t.qr_token=? AND t.is_active AND n.status='ACTIVE' AND b.status='ACTIVE' AND v.status='OPEN'
  """,(r,i)->new Active(new Scope(r.getObject(1,UUID.class),r.getObject(2,UUID.class),r.getObject(3,UUID.class),r.getObject(4,UUID.class)),r.getString(5),r.getString(6),r.getTimestamp(7)==null?null:r.getTimestamp(7).toInstant(),r.getInt(8)),qr).stream().findFirst();
 }
 public boolean issue(Scope s,String hash,Instant expiry){
  int n=jdbc.update("""
  UPDATE table_visits SET dining_code_hash=?,dining_code_expires_at=?,dining_code_failures=0
  WHERE tenant_id=? AND branch_id=? AND table_id=? AND id=? AND status='OPEN'
  """,hash,Timestamp.from(expiry),s.tenant(),s.branch(),s.table(),s.visit());
  if(n==1)jdbc.update("UPDATE dining_sessions SET revoked_at=NOW() WHERE visit_id=? AND revoked_at IS NULL",s.visit());
  return n==1;
 }
 public void fail(UUID visit){jdbc.update("UPDATE table_visits SET dining_code_failures=LEAST(5,dining_code_failures+1) WHERE id=?",visit);}
 public int sessions(UUID visit){return jdbc.queryForObject("SELECT COUNT(*) FROM dining_sessions WHERE visit_id=? AND verified AND revoked_at IS NULL AND expires_at>NOW()",Integer.class,visit);}
 public void insert(UUID id,UUID visit,String hash,Instant expiry){jdbc.update("INSERT INTO dining_sessions(id,visit_id,token_hash,expires_at) VALUES(?,?,?,?)",id,visit,hash,Timestamp.from(expiry));}
 public Optional<Visit> find(String qr,String hash){
  return jdbc.query("""
   SELECT v.tenant_id,v.branch_id,v.table_id,v.id,s.id,v.status,s.verified
   FROM dining_sessions s JOIN table_visits v ON v.id=s.visit_id
   JOIN restaurant_tables t ON t.id=v.table_id AND t.tenant_id=v.tenant_id AND t.branch_id=v.branch_id
   JOIN tenants n ON n.id=v.tenant_id JOIN branches b ON b.id=v.branch_id AND b.tenant_id=v.tenant_id
   WHERE t.qr_token=? AND s.token_hash=? AND s.revoked_at IS NULL AND s.expires_at>NOW()
   AND t.is_active AND n.status='ACTIVE' AND b.status='ACTIVE' AND v.status IN ('OPEN','PAYMENT_PENDING','PAID')
   """,(r,i)->new Visit(new Scope(r.getObject(1,UUID.class),r.getObject(2,UUID.class),r.getObject(3,UUID.class),r.getObject(4,UUID.class)),r.getObject(5,UUID.class),r.getString(6),r.getBoolean(7)),qr,hash).stream().findFirst();
 }
 public boolean hasOrder(Visit v,UUID order){return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM restaurant_orders WHERE tenant_id=? AND branch_id=? AND table_visit_id=? AND id=?)",Boolean.class,v.scope().tenant(),v.scope().branch(),v.scope().visit(),order));}
 public boolean repeated(Visit v,String key){return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM restaurant_orders WHERE tenant_id=? AND table_id=? AND table_visit_id=? AND idempotency_key=?)",Boolean.class,v.scope().tenant(),v.scope().table(),v.scope().visit(),key));}
 public int recent(Visit v){return jdbc.queryForObject("SELECT COUNT(*) FROM restaurant_orders WHERE tenant_id=? AND table_visit_id=? AND created_at>NOW()-INTERVAL '1 minute'",Integer.class,v.scope().tenant(),v.scope().visit());}
 public void record(Visit v,UUID order){jdbc.update("UPDATE restaurant_orders SET dining_session_id=? WHERE tenant_id=? AND branch_id=? AND table_visit_id=? AND id=? AND dining_session_id IS NULL",v.sessionId(),v.scope().tenant(),v.scope().branch(),v.scope().visit(),order);}
}
