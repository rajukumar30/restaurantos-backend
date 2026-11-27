package com.restaurantos.modules.orders.repository;
import com.restaurantos.modules.orders.dto.OrderDto.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.*;
import java.time.*;
import java.sql.Timestamp;
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
   UNION ALL
   SELECT r.order_number,CASE WHEN r.status='PENDING' AND (v.status<>'OPEN' OR r.expires_at<=NOW()) THEN 'CANCELLED' WHEN r.status='PENDING' THEN 'AWAITING_REVIEW' ELSE 'CANCELLED' END,r.total_amount,r.created_at
   FROM order_review_requests r JOIN restaurant_tables rt ON rt.id=r.table_id AND rt.tenant_id=r.tenant_id AND rt.branch_id=r.branch_id
   JOIN table_visits v ON v.id=r.visit_id AND v.tenant_id=r.tenant_id AND v.branch_id=r.branch_id AND v.table_id=r.table_id
   JOIN tenants t ON t.id=r.tenant_id JOIN branches b ON b.id=r.branch_id AND b.tenant_id=r.tenant_id
   WHERE r.id=? AND rt.qr_token=? AND rt.is_active AND t.status='ACTIVE' AND b.status='ACTIVE' AND r.status<>'APPROVED'
   """,(rs,row)->new PublicOrderStatus(rs.getString("order_number"),rs.getString("status"),
    rs.getBigDecimal("total_amount"),rs.getTimestamp("created_at").toInstant()),orderId,qrToken,orderId,qrToken);
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
 public Optional<UUID> updateStatusReturningTable(UUID tenantId,UUID branchId,UUID orderId,String status){
  return jdbc.query("""
   UPDATE restaurant_orders SET status=?,updated_at=NOW()
   WHERE tenant_id=? AND branch_id=? AND id=? AND status=?
   AND NOT EXISTS(SELECT 1 FROM order_disputes d WHERE d.order_id=restaurant_orders.id AND d.status='OPEN')
   RETURNING table_id
   """,(rs,row)->rs.getObject(1,UUID.class),status,tenantId,branchId,orderId,switch(status){case "ACCEPTED"->"NEW";case "PREPARING"->"ACCEPTED";case "READY"->"PREPARING";case "SERVED"->"READY";case "CANCELLED"->"NEW";default->"__INVALID__";}).stream().findFirst();
 }
 public void recordServed(UUID tenantId,UUID branchId,UUID orderId,UUID actorId){
  jdbc.update("""
   INSERT INTO restaurant_order_service_events(tenant_id,branch_id,order_id,actor_id,event_type)
   VALUES(?,?,?,?,?)
   """,tenantId,branchId,orderId,actorId,"WAITER_CONFIRMED_SERVED");
 }
 private static final String ACTIVE="o.status IN ('NEW','ACCEPTED','PREPARING','READY')";
 public String branchTimezone(UUID tenant,UUID branch){
  return jdbc.queryForObject("SELECT timezone FROM branches WHERE tenant_id=? AND id=?",String.class,tenant,branch);
 }
 public OrderWorkspacePage workspace(UUID tenant,OrderWorkspaceQuery q,LocalDate date,LocalDate today,ZoneId zone){
  boolean dateScoped=date!=null&&!"live".equals(q.mode());
  Timestamp todayStart=Timestamp.from(today.atStartOfDay(zone).toInstant());
  boolean live="live".equals(q.mode());
  String scope=" FROM restaurant_orders o WHERE o.tenant_id=? AND o.branch_id=?"+(live?" AND "+ACTIVE:dateScoped?" AND o.created_at>=? AND o.created_at<?":"");
  var scopeArgs=new ArrayList<Object>(List.of(tenant,q.branchId()));if(dateScoped){scopeArgs.add(Timestamp.from(date.atStartOfDay(zone).toInstant()));scopeArgs.add(Timestamp.from(date.plusDays(1).atStartOfDay(zone).toInstant()));}
  Map<String,Long> counts=new LinkedHashMap<>();
  jdbc.query("SELECT o.status,COUNT(*) AS n "+scope+" GROUP BY o.status",rs->{counts.put(rs.getString("status"),rs.getLong("n"));},scopeArgs.toArray());
  counts.put("ALL",counts.values().stream().mapToLong(Long::longValue).sum());
  long[] active=jdbc.queryForObject("SELECT COUNT(*) AS active_count,COUNT(*) FILTER(WHERE o.created_at<?) AS older_count FROM restaurant_orders o WHERE o.tenant_id=? AND o.branch_id=? AND "+ACTIVE,(rs,row)->new long[]{rs.getLong("active_count"),rs.getLong("older_count")},todayStart,tenant,q.branchId());
  var where=new StringBuilder(scope);var args=new ArrayList<>(scopeArgs);
  if(!"ALL".equals(q.status())){where.append(" AND o.status=?");args.add(q.status());}
  String search=q.search().trim();
  if(!search.isEmpty()){
   where.append("""
    AND (strpos(lower(o.order_number),lower(?))>0
    OR EXISTS(SELECT 1 FROM restaurant_tables rt WHERE rt.tenant_id=o.tenant_id AND rt.id=o.table_id AND strpos(lower(rt.display_name),lower(?))>0)
    OR EXISTS(SELECT 1 FROM restaurant_order_items oi WHERE oi.tenant_id=o.tenant_id AND oi.order_id=o.id AND strpos(lower(oi.item_name),lower(?))>0))
    """);args.add(search);args.add(search);args.add(search);
  }
  // Without search, the grouped status counts already contain the exact filtered total.
  long total=search.isEmpty()?counts.getOrDefault(q.status(),0L):jdbc.queryForObject("SELECT COUNT(*) "+where,Long.class,args.toArray());
  int page=Math.min(q.page(),Math.max(1,(int)Math.ceil((double)total/q.size())));
  args.add(q.size());args.add((long)(page-1)*q.size());
  // First select the bounded page; only aggregate line items for those orders.
  var items=jdbc.query("""
   SELECT o.id,o.order_number,o.status,o.total_amount,o.created_at,rt.display_name AS table_name,
    COUNT(oi.id) AS line_count,COALESCE(SUM(oi.quantity),0) AS item_count,
    COALESCE(string_agg(oi.item_name || ' × ' || oi.quantity,', ' ORDER BY oi.created_at),'') AS items_summary
   FROM (SELECT o.id,o.tenant_id,o.table_id,o.order_number,o.status,o.total_amount,o.created_at
   """+where+" ORDER BY o.created_at DESC,o.id DESC LIMIT ? OFFSET ?) o "+"""
   JOIN restaurant_tables rt ON rt.tenant_id=o.tenant_id AND rt.id=o.table_id
   LEFT JOIN restaurant_order_items oi ON oi.tenant_id=o.tenant_id AND oi.order_id=o.id
   GROUP BY o.id,o.order_number,o.status,o.total_amount,o.created_at,rt.display_name
   ORDER BY o.created_at DESC,o.id DESC
   """,(rs,row)->new OrderSummary(rs.getObject("id",UUID.class),rs.getString("order_number"),rs.getString("table_name"),rs.getString("status"),rs.getBigDecimal("total_amount"),rs.getInt("line_count"),rs.getInt("item_count"),rs.getString("items_summary"),rs.getTimestamp("created_at").toInstant()),args.toArray());
  return new OrderWorkspacePage(date,today,zone.getId(),q.mode(),counts,active[0],active[1],items,total,page,q.size());
 }
 public UUID orderTable(UUID tenant,UUID branch,UUID order){
  return jdbc.query("SELECT table_id FROM restaurant_orders WHERE tenant_id=? AND branch_id=? AND id=?",(r,i)->r.getObject(1,UUID.class),tenant,branch,order).stream().findFirst().orElse(null);
 }
}
