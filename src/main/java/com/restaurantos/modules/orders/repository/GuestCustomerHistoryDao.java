package com.restaurantos.modules.orders.repository;
import com.restaurantos.modules.orders.dto.GuestCustomerDto.CustomerOrder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.*;
@Repository
public class GuestCustomerHistoryDao {
 private final JdbcTemplate jdbc;
 public GuestCustomerHistoryDao(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public List<UUID> tenant(String qrToken){
  return jdbc.query("""
   SELECT rt.tenant_id FROM restaurant_tables rt
   JOIN branches b ON b.id=rt.branch_id AND b.tenant_id=rt.tenant_id
   JOIN tenants t ON t.id=rt.tenant_id
   WHERE rt.qr_token=? AND rt.is_active=TRUE AND b.status='ACTIVE' AND t.status='ACTIVE'
   """,(rs,i)->rs.getObject(1,UUID.class),qrToken);
 }
 public int recentChallenges(UUID tenantId,String phone){
  Integer count=jdbc.queryForObject("""
   SELECT COUNT(*) FROM guest_otp_challenges
   WHERE tenant_id=? AND phone=? AND created_at>NOW()-INTERVAL '1 hour'
   """,Integer.class,tenantId,phone);
  return count==null?0:count;
 }
 public void consumePrior(UUID tenantId,String phone){
  jdbc.update("""
   UPDATE guest_otp_challenges SET consumed_at=NOW()
   WHERE tenant_id=? AND phone=? AND consumed_at IS NULL
   """,tenantId,phone);
 }
 public void createChallenge(UUID challengeId,UUID tenantId,String phone){
  jdbc.update("""
   INSERT INTO guest_otp_challenges(id,tenant_id,phone,expires_at)
   VALUES(?,?,?,NOW()+INTERVAL '5 minutes')
   """,challengeId,tenantId,phone);
 }
 public List<String> incrementAndReadPhone(UUID challengeId,UUID tenantId){
  return jdbc.query("""
   UPDATE guest_otp_challenges SET attempts=attempts+1
   WHERE id=? AND tenant_id=? AND consumed_at IS NULL AND expires_at>NOW() AND attempts<5
   RETURNING phone
   """,(rs,i)->rs.getString(1),challengeId,tenantId);
 }
 public void consumeChallenge(UUID challengeId){
  jdbc.update("UPDATE guest_otp_challenges SET consumed_at=NOW() WHERE id=?",challengeId);
 }
 public void createSession(String tokenHash,UUID tenantId,String phone){
  jdbc.update("""
   INSERT INTO guest_customer_sessions(token_hash,tenant_id,phone,expires_at)
   VALUES(?,?,?,NOW()+INTERVAL '7 days')
   """,tokenHash,tenantId,phone);
 }
 public List<String> sessionPhones(String tokenHash,UUID tenantId){
  return jdbc.query("""
   SELECT phone FROM guest_customer_sessions
   WHERE token_hash=? AND tenant_id=? AND expires_at>NOW()
   """,(rs,i)->rs.getString(1),tokenHash,tenantId);
 }
 public List<CustomerOrder> orders(UUID tenantId,String phone){
  return jdbc.query("""
   SELECT o.id,o.order_number,o.status,o.total_amount,o.created_at,rt.display_name,o.customer_name
   FROM restaurant_orders o
   JOIN restaurant_tables rt ON rt.id=o.table_id AND rt.tenant_id=o.tenant_id
   WHERE o.tenant_id=? AND o.customer_phone=?
   ORDER BY o.created_at DESC LIMIT 100
   """,(rs,i)->new CustomerOrder(rs.getObject(1,UUID.class),rs.getString(2),rs.getString(3),
    rs.getBigDecimal(4),rs.getTimestamp(5).toInstant(),rs.getString(6),rs.getString(7)),tenantId,phone);
 }
}
