package com.restaurantos.modules.growth.service.impl;
import com.restaurantos.modules.growth.dto.GrowthDto.*;
import com.restaurantos.modules.growth.repository.GrowthDao;
import com.restaurantos.modules.growth.service.GrowthService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.util.*;
@Service
public class GrowthServiceImpl implements GrowthService {
 private final GrowthDao dao;
 public GrowthServiceImpl(GrowthDao dao){this.dao=dao;}
 private void owner(Authentication auth,UUID tenantId){
  if(auth==null||!(auth.getPrincipal() instanceof UUID userId))
   throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Login required");
  if(!dao.owner(tenantId,userId))
   throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Restaurant owner access required");
 }
 @Transactional(readOnly=true)
 public List<Map<String,Object>> feedback(UUID tenantId,Authentication auth){
  owner(auth,tenantId);return dao.feedback(tenantId);
 }
 @Transactional
 public Map<String,Object> reply(UUID tenantId,UUID feedbackId,ReplyRequest req,Authentication auth){
  owner(auth,tenantId);
  if(dao.reply(tenantId,feedbackId,req.message().trim())==0)
   throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Feedback not found");
  return Map.of("id",feedbackId);
 }
 @Transactional(readOnly=true)
 public List<Map<String,Object>> promotions(UUID tenantId,Authentication auth){
  owner(auth,tenantId);return dao.promotions(tenantId);
 }
 @Transactional
 public Map<String,Object> createPromotion(UUID tenantId,PromotionRequest req,Authentication auth){
  owner(auth,tenantId);
  if(!Set.of("PERCENT","FIXED").contains(req.discountType())||
   ("PERCENT".equals(req.discountType())&&req.discountValue().compareTo(BigDecimal.valueOf(100))>0)||
   !req.endsAt().isAfter(req.startsAt()))
   throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid promotion rules");
  UUID id=UUID.randomUUID();
  try{dao.create(id,tenantId,req);}
  catch(org.springframework.dao.DuplicateKeyException e){
   throw new ResponseStatusException(HttpStatus.CONFLICT,"Coupon code already exists for this restaurant");
  }
  return Map.of("id",id);
 }
 @Transactional
 public Map<String,Object> updatePromotion(UUID tenantId,UUID promotionId,PromotionStatusRequest req,Authentication auth){
  owner(auth,tenantId);
  if(dao.setActive(tenantId,promotionId,req.active())==0)
   throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Promotion not found");
  return Map.of("id",promotionId);
 }
 @Transactional(readOnly=true)
 public List<Map<String,Object>> customers(UUID tenantId,Authentication auth){
  owner(auth,tenantId);return List.of();
 }
}
