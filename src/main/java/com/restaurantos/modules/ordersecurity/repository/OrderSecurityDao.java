package com.restaurantos.modules.ordersecurity.repository;
import com.restaurantos.modules.ordersecurity.dto.OrderSecurityDto.*;
import com.restaurantos.modules.orders.dto.CheckoutDto.Scope;
import com.restaurantos.modules.orders.dto.OrderDto.OrderReceipt;
import com.restaurantos.modules.dining.dto.DiningDto.Visit;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.time.Instant;
import java.sql.*;
import java.util.*;
@Repository
public class OrderSecurityDao{
 private final JdbcTemplate jdbc;public OrderSecurityDao(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public static Settings defaults(){return new Settings("SECURE",new BigDecimal("1000"),new BigDecimal("2000"),6);}
 public Settings settings(UUID tenant,UUID branch){return jdbc.query("SELECT mode,max_order_amount,max_visit_amount,max_units FROM branch_order_security WHERE tenant_id=? AND branch_id=?",(r,i)->new Settings(r.getString(1),r.getBigDecimal(2),r.getBigDecimal(3),r.getInt(4)),tenant,branch).stream().findFirst().orElseGet(OrderSecurityDao::defaults);}
 public void save(UUID tenant,Update r,UUID actor){jdbc.update("""
 INSERT INTO branch_order_security(tenant_id,branch_id,mode,max_order_amount,max_visit_amount,max_units,updated_by) VALUES(?,?,?,?,?,?,?)
 ON CONFLICT(tenant_id,branch_id) DO UPDATE SET mode=EXCLUDED.mode,max_order_amount=EXCLUDED.max_order_amount,max_visit_amount=EXCLUDED.max_visit_amount,max_units=EXCLUDED.max_units,updated_by=EXCLUDED.updated_by,updated_at=NOW()
 """,tenant,r.branchId(),r.mode(),r.maxOrderAmount(),r.maxVisitAmount(),r.maxUnits(),actor);}
 public Optional<Scope> table(String qr){return jdbc.query("""
 SELECT t.tenant_id,t.branch_id,t.id,v.id FROM restaurant_tables t JOIN tenants n ON n.id=t.tenant_id JOIN branches b ON b.id=t.branch_id AND b.tenant_id=t.tenant_id
 LEFT JOIN table_visits v ON v.table_id=t.id AND v.tenant_id=t.tenant_id AND v.branch_id=t.branch_id AND v.status='OPEN' AND v.floor_guest_count IS NOT NULL
 WHERE t.qr_token=? AND t.is_active AND n.status='ACTIVE' AND b.status='ACTIVE'
 """,(r,i)->new Scope(r.getObject(1,UUID.class),r.getObject(2,UUID.class),r.getObject(3,UUID.class),r.getObject(4,UUID.class)),qr).stream().findFirst();}
 public int unverifiedSessions(UUID visit){return jdbc.queryForObject("SELECT COUNT(*) FROM dining_sessions WHERE visit_id=? AND NOT verified AND revoked_at IS NULL AND expires_at>NOW()",Integer.class,visit);}
 public void join(UUID id,UUID visit,String hash,Instant expiry){jdbc.update("INSERT INTO dining_sessions(id,visit_id,token_hash,expires_at,verified) VALUES(?,?,?,?,FALSE)",id,visit,hash,Timestamp.from(expiry));}
 public BigDecimal unverifiedTotal(UUID visit){return jdbc.queryForObject("SELECT COALESCE(SUM(total_amount),0) FROM restaurant_orders WHERE table_visit_id=? AND express_unverified",BigDecimal.class,visit);}
 public void markUnverified(Visit v,UUID order){jdbc.update("UPDATE restaurant_orders SET express_unverified=TRUE WHERE id=? AND tenant_id=? AND branch_id=? AND table_visit_id=?",order,v.scope().tenant(),v.scope().branch(),v.scope().visit());}
 public int recentRequests(UUID visit){return jdbc.queryForObject("SELECT COUNT(*) FROM order_review_requests WHERE visit_id=? AND created_at>NOW()-INTERVAL '1 minute'",Integer.class,visit);}
 public int pending(UUID visit){return jdbc.queryForObject("SELECT COUNT(*) FROM order_review_requests WHERE visit_id=? AND status='PENDING' AND expires_at>NOW()",Integer.class,visit);}
 private static final String SELECT="""
 SELECT r.id,r.table_id,r.visit_id,t.display_name,r.order_number,r.items_summary,r.total_amount,r.reason,r.created_at,r.expires_at,r.session_id,r.request_key,r.request_hash,r.request_json::text,r.status
 FROM order_review_requests r JOIN restaurant_tables t ON t.id=r.table_id AND t.tenant_id=r.tenant_id AND t.branch_id=r.branch_id
 JOIN table_visits v ON v.id=r.visit_id AND v.tenant_id=r.tenant_id AND v.branch_id=r.branch_id AND v.table_id=r.table_id
 """;
 private Review review(ResultSet r,int i)throws SQLException{return new Review(r.getObject(1,UUID.class),r.getObject(2,UUID.class),r.getObject(3,UUID.class),r.getString(4),r.getString(5),r.getString(6),r.getBigDecimal(7),r.getString(8),r.getTimestamp(9).toInstant(),r.getTimestamp(10).toInstant());}
 private Stored stored(ResultSet r,int i)throws SQLException{return new Stored(review(r,i),r.getObject(11,UUID.class),r.getString(12),r.getString(13),r.getString(14),r.getString(15));}
 public Optional<Stored> replay(Scope s,String key){return jdbc.query(SELECT+" WHERE r.tenant_id=? AND r.branch_id=? AND r.visit_id=? AND r.request_key=?",this::stored,s.tenant(),s.branch(),s.visit(),key).stream().findFirst();}
 public List<Review> list(UUID tenant,UUID branch){return jdbc.query(SELECT+" WHERE r.tenant_id=? AND r.branch_id=? AND v.status='OPEN' AND r.status='PENDING' AND r.expires_at>NOW() ORDER BY r.created_at LIMIT 100",this::review,tenant,branch);}
 public Optional<Stored> find(UUID tenant,UUID branch,UUID id){return jdbc.query(SELECT+" WHERE r.tenant_id=? AND r.branch_id=? AND r.id=?",this::stored,tenant,branch,id).stream().findFirst();}
 public boolean open(Scope s){return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM table_visits WHERE tenant_id=? AND branch_id=? AND table_id=? AND id=? AND status='OPEN')",Boolean.class,s.tenant(),s.branch(),s.table(),s.visit()));}
 public String qr(Scope s){return jdbc.queryForObject("SELECT qr_token FROM restaurant_tables WHERE tenant_id=? AND branch_id=? AND id=? AND is_active",String.class,s.tenant(),s.branch(),s.table());}
 public boolean sessionActive(Scope s,UUID session){return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM dining_sessions WHERE id=? AND visit_id=? AND revoked_at IS NULL AND expires_at>NOW())",Boolean.class,session,s.visit()));}
 public void verifySession(Scope s,UUID session){jdbc.update("UPDATE dining_sessions SET verified=TRUE WHERE id=? AND visit_id=? AND revoked_at IS NULL AND expires_at>NOW()",session,s.visit());}
 public void insert(Visit v,UUID id,String number,String key,String hash,String json,BigDecimal total,String items,String reason,Instant expiry){var s=v.scope();jdbc.update("""
 INSERT INTO order_review_requests(id,tenant_id,branch_id,table_id,visit_id,session_id,order_number,request_key,request_hash,request_json,total_amount,items_summary,reason,expires_at)
 VALUES(?,?,?,?,?,?,?,?,?,CAST(? AS jsonb),?,?,?,?)
 """,id,s.tenant(),s.branch(),s.table(),s.visit(),v.sessionId(),number,key,hash,json,total,items,reason,Timestamp.from(expiry));}
 public void resolve(Scope s,UUID id,UUID actor,String status,String reason){if(jdbc.update("UPDATE order_review_requests SET status=?,resolved_by=?,resolved_at=NOW(),resolution=? WHERE id=? AND tenant_id=? AND branch_id=? AND visit_id=? AND status='PENDING' AND expires_at>NOW()",status,actor,reason,id,s.tenant(),s.branch(),s.visit())!=1)throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,"Order review expired or changed.");}
}
