package com.restaurantos.modules.floor.repository;
import com.restaurantos.modules.floor.dto.FloorDto.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.*;
import java.time.Instant;
@Repository
public class FloorDao {
 private final JdbcTemplate jdbc;
 public FloorDao(JdbcTemplate jdbc){this.jdbc=jdbc;}
 private static Instant instant(java.sql.ResultSet rs,String key)throws java.sql.SQLException{var t=rs.getTimestamp(key);return t==null?null:t.toInstant();}
 private static final String STATE="""
 SELECT rt.id,rt.table_number,rt.display_name,rt.seating_capacity,rt.is_active,
 v.id visit_id,v.status visit_status,v.floor_guest_count,v.opened_at,
 COALESCE(v.floor_grace_until,v.floor_paid_at+INTERVAL '5 minutes') grace_until,v.floor_cleaning_started_at,
 COALESCE(o.new_count,0) new_count,COALESCE(o.preparing,0) preparing,COALESCE(o.ready,0) ready,COALESCE(o.served,0) served,
 COALESCE(o.bill,0) bill,COALESCE(s.requests,0) requests,COALESCE(s.bill_requested,false) bill_requested,COALESCE(s.cleaning_requested,false) cleaning_requested,s.oldest_request,o.oldest_new,o.oldest_ready
 FROM restaurant_tables rt
 LEFT JOIN table_visits v ON v.tenant_id=rt.tenant_id AND v.branch_id=rt.branch_id AND v.table_id=rt.id AND v.status IN ('OPEN','PAYMENT_PENDING','PAID')
 LEFT JOIN LATERAL (SELECT COUNT(*) FILTER(WHERE status='NEW') new_count,COUNT(*) FILTER(WHERE status IN ('ACCEPTED','PREPARING')) preparing,
 COUNT(*) FILTER(WHERE status='READY') ready,COUNT(*) FILTER(WHERE status='SERVED') served, SUM(total_amount) FILTER(WHERE status<>'CANCELLED') bill,
 MIN(created_at) FILTER(WHERE status='NEW') oldest_new,MIN(updated_at) FILTER(WHERE status='READY') oldest_ready
 FROM restaurant_orders o WHERE o.tenant_id=rt.tenant_id AND o.branch_id=rt.branch_id AND o.table_visit_id=v.id) o ON true
 LEFT JOIN LATERAL (SELECT COUNT(*) requests,BOOL_OR(request_type='BILL') bill_requested,BOOL_OR(request_type='CLEANING') cleaning_requested,MIN(created_at) oldest_request
 FROM service_requests s WHERE s.tenant_id=rt.tenant_id AND s.branch_id=rt.branch_id AND s.table_id=rt.id AND s.status IN ('REQUESTED','ON_THE_WAY') AND (v.id IS NULL OR s.created_at>=v.opened_at)) s ON true
 WHERE rt.tenant_id=? AND rt.branch_id=?
 """;
 private TableState map(java.sql.ResultSet r,int i)throws java.sql.SQLException{
  return new TableState(r.getObject("id",UUID.class),r.getString("table_number"),r.getString("display_name"),r.getInt("seating_capacity"),r.getBoolean("is_active"),r.getObject("visit_id",UUID.class),r.getString("visit_status"),r.getObject("floor_guest_count",Integer.class),instant(r,"opened_at"),instant(r,"grace_until"),instant(r,"floor_cleaning_started_at"),r.getLong("new_count"),r.getLong("preparing"),r.getLong("ready"),r.getLong("served"),r.getBigDecimal("bill"),r.getLong("requests"),r.getBoolean("bill_requested"),r.getBoolean("cleaning_requested"),instant(r,"oldest_request"),instant(r,"oldest_new"),instant(r,"oldest_ready"));
 }
 public List<TableState> list(UUID tenant,UUID branch){return jdbc.query(STATE+" ORDER BY rt.table_number",this::map,tenant,branch);}
 public Optional<TableState> one(UUID tenant,UUID branch,UUID table){return jdbc.query(STATE+" AND rt.id=?",this::map,tenant,branch,table).stream().findFirst();}
 public boolean lock(UUID tenant,UUID branch,UUID table){return !jdbc.query("SELECT id FROM restaurant_tables WHERE tenant_id=? AND branch_id=? AND id=? FOR UPDATE",(r,i)->r.getObject(1,UUID.class),tenant,branch,table).isEmpty();}
 public void seat(UUID tenant,UUID branch,UUID table,UUID visit,int guests){jdbc.update("INSERT INTO table_visits(id,tenant_id,branch_id,table_id,bill_number,status,floor_guest_count) VALUES(?,?,?,?,?,'OPEN',?)",visit,tenant,branch,table,"B"+visit.toString().replace("-","").substring(0,20).toUpperCase(Locale.ROOT),guests);}
 public void guests(UUID visit,int guests){jdbc.update("UPDATE table_visits SET floor_guest_count=? WHERE id=?",guests,visit);}
 public void extend(UUID visit){jdbc.update("UPDATE table_visits SET floor_grace_until=NOW()+INTERVAL '5 minutes',floor_cleaning_started_at=NULL WHERE id=?",visit);}
 public void start(UUID visit){jdbc.update("UPDATE table_visits SET floor_cleaning_started_at=NOW() WHERE id=?",visit);}
 public void close(UUID tenant,UUID branch,UUID table,UUID visit){
  jdbc.update("UPDATE table_visits SET status='CLOSED',closed_at=NOW(),floor_cleaned_at=NOW() WHERE id=?",visit);
  jdbc.update("UPDATE service_requests SET status='CANCELLED',cancelled_at=NOW() WHERE tenant_id=? AND branch_id=? AND table_id=? AND status IN ('REQUESTED','ON_THE_WAY')",tenant,branch,table);
 }
 public void audit(UUID tenant,UUID branch,UUID table,UUID visit,UUID actor,String action){jdbc.update("INSERT INTO floor_events(tenant_id,branch_id,table_id,visit_id,actor_id,action) VALUES(?,?,?,?,?,?)",tenant,branch,table,visit,actor,action);}
 public List<FloorOrder> orders(UUID tenant,UUID branch,UUID visit){
  if(visit==null)return List.of();
  return jdbc.query("""
   SELECT o.id,o.order_number,o.status,o.total_amount,o.created_at,COALESCE(string_agg(i.item_name||' × '||i.quantity,', ' ORDER BY i.created_at),'') items
   FROM (SELECT * FROM restaurant_orders WHERE tenant_id=? AND branch_id=? AND table_visit_id=? ORDER BY created_at DESC,id DESC LIMIT 50) o
   LEFT JOIN restaurant_order_items i ON i.tenant_id=o.tenant_id AND i.order_id=o.id GROUP BY o.id,o.order_number,o.status,o.total_amount,o.created_at ORDER BY o.created_at DESC
   """,(r,i)->new FloorOrder(r.getObject("id",UUID.class),r.getString("order_number"),r.getString("status"),r.getBigDecimal("total_amount"),instant(r,"created_at"),r.getString("items")),tenant,branch,visit);
 }
 public List<FloorRequest> requests(UUID tenant,UUID branch,UUID table){return jdbc.query("""
 SELECT id,request_type,status,created_at FROM service_requests WHERE tenant_id=? AND branch_id=? AND table_id=? AND status IN ('REQUESTED','ON_THE_WAY') ORDER BY created_at LIMIT 50
 """,(r,i)->new FloorRequest(r.getObject("id",UUID.class),r.getString("request_type"),r.getString("status"),instant(r,"created_at")),tenant,branch,table);}
}
