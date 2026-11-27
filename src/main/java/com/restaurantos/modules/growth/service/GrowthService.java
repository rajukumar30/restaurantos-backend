package com.restaurantos.modules.growth.service;
import com.restaurantos.modules.growth.dto.GrowthDto.*;
import org.springframework.security.core.Authentication;
import java.util.*;
public interface GrowthService {
 List<Map<String,Object>> feedback(UUID tenantId,Authentication auth);
 Map<String,Object> reply(UUID tenantId,UUID feedbackId,ReplyRequest req,Authentication auth);
 List<Map<String,Object>> promotions(UUID tenantId,Authentication auth);
 Map<String,Object> createPromotion(UUID tenantId,PromotionRequest req,Authentication auth);
 Map<String,Object> updatePromotion(UUID tenantId,UUID promotionId,PromotionStatusRequest req,Authentication auth);
 List<Map<String,Object>> customers(UUID tenantId,Authentication auth);
}
