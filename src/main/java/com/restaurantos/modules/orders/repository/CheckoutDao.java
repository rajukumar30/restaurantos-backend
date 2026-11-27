package com.restaurantos.modules.orders.repository;
import com.restaurantos.modules.orders.dto.CheckoutDto.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.*;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
@Repository
public class CheckoutDao{
 private final JdbcTemplate jdbc;
 public CheckoutDao(JdbcTemplate jdbc){this.jdbc=jdbc;}
 private static final String FROM="""
 FROM table_visits v JOIN restaurant_tables rt ON rt.id=v.table_id AND rt.tenant_id=v.tenant_id AND rt.branch_id=v.branch_id
 LEFT JOIN branch_checkout_settings m ON m.tenant_id=v.tenant_id AND m.branch_id=v.branch_id
 """;
 private static final String SELECT="""
 SELECT v.id,v.table_id,rt.display_name,v.bill_number,v.status,v.opened_at,
 COALESCE(o.total,0) total,COALESCE(o.paid,0) paid,COALESCE(o.count,0) order_count,COALESCE(o.unfinished,0) unfinished,
 CASE WHEN v.status='PAYMENT_PENDING' THEN v.checkout_upi_id WHEN v.status='OPEN' THEN m.upi_id END upi_id,
 CASE WHEN v.status='PAYMENT_PENDING' THEN v.checkout_merchant_name WHEN v.status='OPEN' THEN m.merchant_name END merchant_name,
 v.checkout_requested_at,(SELECT COUNT(*) FROM order_disputes d WHERE d.visit_id=v.id AND d.tenant_id=v.tenant_id AND d.branch_id=v.branch_id AND d.status='OPEN') disputes
 """;
 private static final String AGG="""
 LEFT JOIN LATERAL (
 SELECT SUM(o.total_amount) FILTER(WHERE o.status<>'CANCELLED') total,
 SUM(COALESCE(p.amount,0)) FILTER(WHERE o.status<>'CANCELLED') paid,
 COUNT(*) FILTER(WHERE o.status<>'CANCELLED') count,
 COUNT(*) FILTER(WHERE o.status NOT IN ('SERVED','CANCELLED')) unfinished
 FROM restaurant_orders o LEFT JOIN LATERAL(
 SELECT SUM(amount) amount FROM restaurant_order_payments p WHERE p.tenant_id=o.tenant_id AND p.branch_id=o.branch_id AND p.order_id=o.id
 ) p ON TRUE WHERE o.tenant_id=v.tenant_id AND o.branch_id=v.branch_id AND o.table_visit_id=v.id
 ) o ON TRUE
 """;
 private Bill map(ResultSet r,int i)throws SQLException{
  var total=r.getBigDecimal("total");var paid=r.getBigDecimal("paid");var requested=r.getTimestamp("checkout_requested_at");
  return new Bill(r.getObject("id",UUID.class),r.getObject("table_id",UUID.class),r.getString("display_name"),r.getString("bill_number"),r.getString("status"),r.getTimestamp("opened_at").toInstant(),total,paid,total.subtract(paid).max(BigDecimal.ZERO),r.getInt("order_count"),r.getInt("unfinished"),r.getString("upi_id"),r.getString("merchant_name"),requested==null?null:requested.toInstant(),r.getInt("disputes"));
 }
 public Optional<Scope> guest(String qr,UUID order){
  // The random order UUID is the existing private tracking capability. QR or phone alone cannot reveal a bill.
  return jdbc.query("""
   SELECT v.tenant_id,v.branch_id,v.table_id,v.id FROM restaurant_orders o JOIN table_visits v ON v.id=o.table_visit_id AND v.tenant_id=o.tenant_id AND v.branch_id=o.branch_id AND v.table_id=o.table_id
   JOIN restaurant_tables t ON t.id=v.table_id AND t.tenant_id=v.tenant_id AND t.branch_id=v.branch_id
   JOIN tenants n ON n.id=v.tenant_id JOIN branches b ON b.id=v.branch_id AND b.tenant_id=v.tenant_id
   WHERE t.qr_token=? AND o.id=? AND t.is_active AND n.status='ACTIVE' AND b.status='ACTIVE'
   """,(r,i)->new Scope(r.getObject(1,UUID.class),r.getObject(2,UUID.class),r.getObject(3,UUID.class),r.getObject(4,UUID.class)),qr,order).stream().findFirst();
 }
 public Optional<Scope> scope(UUID tenant,UUID branch,UUID visit){
  return jdbc.query("SELECT tenant_id,branch_id,table_id,id FROM table_visits WHERE tenant_id=? AND branch_id=? AND id=?",(r,i)->new Scope(r.getObject(1,UUID.class),r.getObject(2,UUID.class),r.getObject(3,UUID.class),r.getObject(4,UUID.class)),tenant,branch,visit).stream().findFirst();
 }
 public void lock(Scope s){
  jdbc.queryForObject("SELECT id FROM restaurant_tables WHERE tenant_id=? AND branch_id=? AND id=? FOR UPDATE",UUID.class,s.tenant(),s.branch(),s.table());
  jdbc.queryForObject("SELECT id FROM table_visits WHERE tenant_id=? AND branch_id=? AND id=? FOR UPDATE",UUID.class,s.tenant(),s.branch(),s.visit());
 }
 public Bill bill(Scope s){return jdbc.queryForObject(SELECT+FROM+AGG+" WHERE v.tenant_id=? AND v.branch_id=? AND v.id=?",this::map,s.tenant(),s.branch(),s.visit());}
 public Detail detail(Scope s){return new Detail(bill(s),jdbc.query("""
 SELECT o.order_number,i.item_name,i.quantity,i.line_total FROM restaurant_orders o JOIN restaurant_order_items i ON i.tenant_id=o.tenant_id AND i.order_id=o.id
 WHERE o.tenant_id=? AND o.branch_id=? AND o.table_visit_id=? AND o.status<>'CANCELLED' ORDER BY o.created_at,o.id,i.created_at,i.id
 """,(r,i)->new Line(r.getString(1),r.getString(2),r.getInt(3),r.getBigDecimal(4)),s.tenant(),s.branch(),s.visit()),disputes(s));}
 public Page page(UUID tenant,UUID branch,String filter,String search,int page,int size){
  String where=" WHERE v.tenant_id=? AND v.branch_id=? AND EXISTS(SELECT 1 FROM restaurant_orders o WHERE o.tenant_id=v.tenant_id AND o.branch_id=v.branch_id AND o.table_visit_id=v.id AND o.status<>'CANCELLED')";
  if("pending".equals(filter))where+=" AND v.status IN ('OPEN','PAYMENT_PENDING')";
  if("paid".equals(filter))where+=" AND v.status IN ('PAID','CLOSED') AND v.floor_paid_at IS NOT NULL";
  var args=new ArrayList<Object>(List.of(tenant,branch));
  if(!search.isBlank()){where+=" AND (strpos(lower(rt.display_name),lower(?))>0 OR strpos(lower(v.bill_number),lower(?))>0)";args.add(search);args.add(search);}
  long total=jdbc.queryForObject("SELECT COUNT(*) "+FROM+where,Long.class,args.toArray());
  int p=Math.min(page,Math.max(1,(int)Math.ceil((double)total/size)));args.add(size);args.add((long)(p-1)*size);
  // Select the page first. Never aggregate the branch's entire order history.
  var bills=jdbc.query(SELECT+FROM+AGG+" WHERE v.id IN (SELECT v.id "+FROM+where+" ORDER BY v.opened_at DESC,v.id LIMIT ? OFFSET ?) ORDER BY v.opened_at DESC,v.id",this::map,args.toArray());
  return new Page(bills,total,p,size);
 }
 public Merchant merchant(UUID tenant,UUID branch){return jdbc.query("SELECT upi_id,merchant_name FROM branch_checkout_settings WHERE tenant_id=? AND branch_id=?",(r,i)->new Merchant(r.getString(1),r.getString(2)),tenant,branch).stream().findFirst().orElse(new Merchant(null,null));}
 public void merchant(UUID tenant,UUID branch,Merchant m){jdbc.update("""
 INSERT INTO branch_checkout_settings(tenant_id,branch_id,upi_id,merchant_name) VALUES(?,?,?,?)
 ON CONFLICT(tenant_id,branch_id) DO UPDATE SET upi_id=EXCLUDED.upi_id,merchant_name=EXCLUDED.merchant_name,updated_at=NOW()
 """,tenant,branch,m.upiId(),m.merchantName());}
 public void begin(Scope s,Bill b){jdbc.update("""
 UPDATE table_visits SET status='PAYMENT_PENDING',checkout_amount=?,checkout_upi_id=?,checkout_merchant_name=?,checkout_requested_at=NOW()
 WHERE tenant_id=? AND branch_id=? AND id=? AND status='OPEN'
 """,b.due(),b.upiId(),b.merchantName(),s.tenant(),s.branch(),s.visit());
 jdbc.update("UPDATE order_review_requests SET status='REJECTED',resolved_at=NOW(),resolution='Checkout started' WHERE tenant_id=? AND branch_id=? AND visit_id=? AND status='PENDING'",s.tenant(),s.branch(),s.visit());}
 public BigDecimal frozen(Scope s){return jdbc.queryForObject("SELECT checkout_amount FROM table_visits WHERE tenant_id=? AND branch_id=? AND id=?",BigDecimal.class,s.tenant(),s.branch(),s.visit());}
 public void resume(Scope s){jdbc.update("UPDATE table_visits SET status='OPEN',checkout_amount=NULL,checkout_upi_id=NULL,checkout_merchant_name=NULL,checkout_requested_at=NULL WHERE tenant_id=? AND branch_id=? AND id=? AND status='PAYMENT_PENDING'",s.tenant(),s.branch(),s.visit());}
 public Optional<Map<String,Object>> replay(Scope s,UUID key){return jdbc.queryForList("SELECT provider,provider_order_id,amount,visit_id FROM table_bill_payments WHERE tenant_id=? AND id=? AND status='PAID'",s.tenant(),key).stream().findFirst();}
 public List<Allocation> allocations(Scope s){return jdbc.query("""
 SELECT o.id,o.total_amount-COALESCE((SELECT SUM(p.amount) FROM restaurant_order_payments p WHERE p.tenant_id=o.tenant_id AND p.branch_id=o.branch_id AND p.order_id=o.id),0) due
 FROM restaurant_orders o WHERE o.tenant_id=? AND o.branch_id=? AND o.table_visit_id=? AND o.status='SERVED' ORDER BY o.id FOR UPDATE OF o
 """,(r,i)->new Allocation(r.getObject(1,UUID.class),r.getBigDecimal(2)),s.tenant(),s.branch(),s.visit());}
 public boolean referenceUsed(Scope s,String method,String ref){return ref!=null&&Boolean.TRUE.equals(jdbc.queryForObject("""
 SELECT EXISTS(SELECT 1 FROM restaurant_order_payments WHERE tenant_id=? AND method=? AND upper(reference)=?)
 """,Boolean.class,s.tenant(),method,ref));}
 public void record(Scope s,UUID key,String method,String ref,BigDecimal amount){
  jdbc.update("INSERT INTO table_bill_payments(id,tenant_id,visit_id,provider,provider_order_id,provider_payment_id,amount,status,confirmed_at) VALUES(?,?,?,?,?,?,?,'PAID',NOW())",key,s.tenant(),s.visit(),"MANUAL_"+method,ref,key.toString(),amount);
 }
 public void allocate(Scope s,Allocation a,String method,String ref,UUID actor){jdbc.update("""
 INSERT INTO restaurant_order_payments(id,tenant_id,branch_id,order_id,amount,method,reference,recorded_by) VALUES(?,?,?,?,?,?,?,?)
 """,UUID.randomUUID(),s.tenant(),s.branch(),a.orderId(),a.due(),method,ref,actor);}
 public void settle(Scope s){jdbc.update("""
 UPDATE table_visits SET status='PAID',floor_paid_at=NOW(),floor_grace_until=NOW()+INTERVAL '5 minutes' WHERE tenant_id=? AND branch_id=? AND id=? AND status='PAYMENT_PENDING'
 """,s.tenant(),s.branch(),s.visit());}
 public List<Dispute> disputes(Scope s){return jdbc.query("""
 SELECT d.id,o.order_number,o.status order_status,d.status,d.reason,d.resolution,d.created_at
 FROM order_disputes d JOIN restaurant_orders o ON o.id=d.order_id AND o.tenant_id=d.tenant_id AND o.branch_id=d.branch_id AND o.table_visit_id=d.visit_id
 WHERE d.tenant_id=? AND d.branch_id=? AND d.visit_id=? ORDER BY d.created_at DESC
 """,(r,i)->new Dispute(r.getObject(1,UUID.class),r.getString(2),r.getString(3),r.getString(4),r.getString(5),r.getString(6),r.getTimestamp(7).toInstant()),s.tenant(),s.branch(),s.visit());}
 public boolean report(Scope s,UUID session,String number,String reason){
  var ids=jdbc.query("SELECT id FROM restaurant_orders WHERE tenant_id=? AND branch_id=? AND table_visit_id=? AND order_number=? AND status<>'CANCELLED' FOR UPDATE",(r,i)->r.getObject(1,UUID.class),s.tenant(),s.branch(),s.visit(),number);
  if(ids.isEmpty())return false;
  jdbc.update("""
 INSERT INTO order_disputes(id,tenant_id,branch_id,visit_id,order_id,session_id,reason,status)
 VALUES(?,?,?,?,?,?,?,'OPEN') ON CONFLICT(order_id) DO NOTHING
 """,UUID.randomUUID(),s.tenant(),s.branch(),s.visit(),ids.getFirst(),session,reason);return true;
 }
 public Optional<UUID> disputedOrder(Scope s,UUID dispute){
  return jdbc.query("SELECT order_id FROM order_disputes WHERE id=? AND tenant_id=? AND branch_id=? AND visit_id=? AND status='OPEN' FOR UPDATE",(r,i)->r.getObject(1,UUID.class),dispute,s.tenant(),s.branch(),s.visit()).stream().findFirst();
 }
 public boolean voidUnpaidOrder(Scope s,UUID order){return jdbc.update("""
 UPDATE restaurant_orders o SET status='CANCELLED',updated_at=NOW()
 WHERE o.tenant_id=? AND o.branch_id=? AND o.table_visit_id=? AND o.id=?
 AND NOT EXISTS(SELECT 1 FROM restaurant_order_payments p WHERE p.tenant_id=o.tenant_id AND p.branch_id=o.branch_id AND p.order_id=o.id)
 """,s.tenant(),s.branch(),s.visit(),order)==1;}
 public void resolve(Scope s,UUID dispute,UUID actor,String status,String reason){jdbc.update("""
 UPDATE order_disputes SET status=?,resolution=?,resolved_by=?,resolved_at=NOW() WHERE id=? AND tenant_id=? AND branch_id=? AND visit_id=? AND status='OPEN'
 """,status,reason,actor,dispute,s.tenant(),s.branch(),s.visit());}
}
