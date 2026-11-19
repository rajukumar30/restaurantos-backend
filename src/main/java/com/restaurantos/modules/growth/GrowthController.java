package com.restaurantos.modules.growth;

import com.restaurantos.common.api.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}")
public class GrowthController {
 private final JdbcTemplate jdbc;
 public GrowthController(JdbcTemplate jdbc){this.jdbc=jdbc;}
 private void owner(Authentication auth,UUID tenantId){
  if(auth==null || !(auth.getPrincipal() instanceof UUID userId))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Login required");
  Integer count=jdbc.queryForObject("""
   SELECT COUNT(*) FROM tenant_users tu
   JOIN tenants t ON t.id=tu.tenant_id AND t.status='ACTIVE'
   JOIN user_roles ur ON ur.tenant_id=tu.tenant_id AND ur.tenant_user_id=tu.id
   JOIN roles r ON r.id=ur.role_id
   WHERE tu.tenant_id=? AND tu.user_id=? AND tu.status='ACTIVE'
   AND ur.branch_id IS NULL AND UPPER(r.display_name) LIKE '%OWNER%'
   """,Integer.class,tenantId,userId);
  if(count==null||count==0)throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Restaurant owner access required");
 }
 @GetMapping("/feedback")
 public ApiResponse<List<Map<String,Object>>> feedback(@PathVariable UUID tenantId,Authentication auth){
  owner(auth,tenantId);
  return ApiResponse.success("Feedback loaded",jdbc.queryForList("""
   SELECT id,rating,comment,customer_name AS "customerName",
   created_at AS "createdAt",owner_reply AS "ownerReply"
   FROM customer_feedback WHERE tenant_id=? ORDER BY created_at DESC LIMIT 500
   """,tenantId));
 }
 public record ReplyRequest(@NotBlank @Size(max=1000) String message){}
 @PostMapping("/feedback/{feedbackId}/reply")
 @Transactional
 public ApiResponse<Map<String,Object>> reply(@PathVariable UUID tenantId,@PathVariable UUID feedbackId,
  @Valid @RequestBody ReplyRequest req,Authentication auth){
  owner(auth,tenantId);
  int n=jdbc.update("UPDATE customer_feedback SET owner_reply=? WHERE tenant_id=? AND id=?",
   req.message().trim(),tenantId,feedbackId);
  if(n==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Feedback not found");
  return ApiResponse.success("Reply saved",Map.of("id",feedbackId));
 }
 @GetMapping("/promotions")
 public ApiResponse<List<Map<String,Object>>> promotions(@PathVariable UUID tenantId,Authentication auth){
  owner(auth,tenantId);
  return ApiResponse.success("Promotions loaded",jdbc.queryForList("""
   SELECT id,code,description,discount_type AS "discountType",
   discount_value AS "discountValue",minimum_order_amount AS "minimumOrderAmount",
   starts_at AS "startsAt",ends_at AS "endsAt",usage_limit AS "usageLimit",
   usage_count AS "usageCount",active
   FROM restaurant_promotions WHERE tenant_id=? ORDER BY created_at DESC LIMIT 500
   """,tenantId));
 }
 public record PromotionRequest(@NotBlank @Pattern(regexp="[A-Za-z0-9_-]{3,40}") String code,
  @Size(max=500) String description,@NotBlank String discountType,
  @NotNull @DecimalMin("0.01") BigDecimal discountValue,
  @NotNull @DecimalMin("0.00") BigDecimal minimumOrderAmount,
  @NotNull OffsetDateTime startsAt,@NotNull OffsetDateTime endsAt,
  @Min(1) Integer usageLimit){}
 @PostMapping("/promotions")
 @Transactional
 public ApiResponse<Map<String,Object>> createPromotion(@PathVariable UUID tenantId,
  @Valid @RequestBody PromotionRequest req,Authentication auth){
  owner(auth,tenantId);
  if(!Set.of("PERCENT","FIXED").contains(req.discountType()) ||
   ("PERCENT".equals(req.discountType())&&req.discountValue().compareTo(BigDecimal.valueOf(100))>0) ||
   !req.endsAt().isAfter(req.startsAt()))
   throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid promotion rules");
  UUID id=UUID.randomUUID();
  try{jdbc.update("""
   INSERT INTO restaurant_promotions
   (id,tenant_id,code,description,discount_type,discount_value,minimum_order_amount,starts_at,ends_at,usage_limit)
   VALUES (?,?,?,?,?,?,?,?,?,?)
   """,id,tenantId,req.code().toUpperCase(Locale.ROOT),req.description(),
   req.discountType(),req.discountValue(),req.minimumOrderAmount(),
   Timestamp.from(req.startsAt().toInstant()),Timestamp.from(req.endsAt().toInstant()),req.usageLimit());
  }catch(org.springframework.dao.DuplicateKeyException e){
   throw new ResponseStatusException(HttpStatus.CONFLICT,"Coupon code already exists for this restaurant");
  }
  return ApiResponse.success("Promotion created",Map.of("id",id));
 }
 public record PromotionStatusRequest(@NotNull Boolean active){}
 @PatchMapping("/promotions/{promotionId}")
 @Transactional
 public ApiResponse<Map<String,Object>> updatePromotion(@PathVariable UUID tenantId,
  @PathVariable UUID promotionId,@Valid @RequestBody PromotionStatusRequest req,Authentication auth){
  owner(auth,tenantId);
  int n=jdbc.update("UPDATE restaurant_promotions SET active=? WHERE id=? AND tenant_id=?",
   req.active(),promotionId,tenantId);
  if(n==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Promotion not found");
  return ApiResponse.success("Promotion updated",Map.of("id",promotionId));
 }
 @GetMapping("/customers/analytics")
 public ApiResponse<List<Map<String,Object>>> customers(@PathVariable UUID tenantId,Authentication auth){
  owner(auth,tenantId);
  // Do not infer customer identity from non-unique free-text names on anonymous QR orders.
  return ApiResponse.success("No identified customer profiles yet",List.of());
 }
}
