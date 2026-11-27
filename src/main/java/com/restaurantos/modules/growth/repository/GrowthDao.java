package com.restaurantos.modules.growth.repository;
import com.restaurantos.modules.growth.dto.GrowthDto.PromotionRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.Timestamp;
import java.util.*;
@Repository
public class GrowthDao {
 private final JdbcTemplate jdbc;
 public GrowthDao(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public boolean owner(UUID tenantId,UUID userId){
  Integer count=jdbc.queryForObject("""
   SELECT COUNT(*) FROM tenant_users tu
   JOIN tenants t ON t.id=tu.tenant_id AND t.status='ACTIVE'
   JOIN user_roles ur ON ur.tenant_id=tu.tenant_id AND ur.tenant_user_id=tu.id
   JOIN roles r ON r.id=ur.role_id
   WHERE tu.tenant_id=? AND tu.user_id=? AND tu.status='ACTIVE'
    AND ur.branch_id IS NULL AND UPPER(r.display_name) LIKE '%OWNER%'
   """,Integer.class,tenantId,userId);
  return count!=null&&count>0;
 }
 public List<Map<String,Object>> feedback(UUID tenantId){
  return jdbc.queryForList("""
   SELECT id,rating,comment,customer_name AS "customerName",
    created_at AS "createdAt",owner_reply AS "ownerReply"
   FROM customer_feedback WHERE tenant_id=? ORDER BY created_at DESC LIMIT 500
   """,tenantId);
 }
 public int reply(UUID tenantId,UUID id,String message){
  return jdbc.update("UPDATE customer_feedback SET owner_reply=? WHERE tenant_id=? AND id=?",message,tenantId,id);
 }
 public List<Map<String,Object>> promotions(UUID tenantId){
  return jdbc.queryForList("""
   SELECT id,code,description,discount_type AS "discountType",
    discount_value AS "discountValue",minimum_order_amount AS "minimumOrderAmount",
    starts_at AS "startsAt",ends_at AS "endsAt",usage_limit AS "usageLimit",
    usage_count AS "usageCount",active
   FROM restaurant_promotions WHERE tenant_id=? ORDER BY created_at DESC LIMIT 500
   """,tenantId);
 }
 public void create(UUID id,UUID tenantId,PromotionRequest req){
  jdbc.update("""
   INSERT INTO restaurant_promotions
    (id,tenant_id,code,description,discount_type,discount_value,minimum_order_amount,starts_at,ends_at,usage_limit)
   VALUES(?,?,?,?,?,?,?,?,?,?)
   """,id,tenantId,req.code().toUpperCase(Locale.ROOT),req.description(),
   req.discountType(),req.discountValue(),req.minimumOrderAmount(),
   Timestamp.from(req.startsAt().toInstant()),Timestamp.from(req.endsAt().toInstant()),req.usageLimit());
 }
 public int setActive(UUID tenantId,UUID promotionId,boolean active){
  return jdbc.update("UPDATE restaurant_promotions SET active=? WHERE id=? AND tenant_id=?",
   active,promotionId,tenantId);
 }
}
