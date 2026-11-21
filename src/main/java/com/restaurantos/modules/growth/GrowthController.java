package com.restaurantos.modules.growth;
import com.restaurantos.common.api.ApiResponse;
import com.restaurantos.modules.growth.dto.GrowthDto.*;
import com.restaurantos.modules.growth.service.GrowthService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController
@RequestMapping("/api/v1/tenants/{tenantId}")
public class GrowthController {
 private final GrowthService service;
 public GrowthController(GrowthService service){this.service=service;}
 @GetMapping("/feedback")
 public ApiResponse<List<Map<String,Object>>> feedback(@PathVariable UUID tenantId,Authentication auth){
  return ApiResponse.success("Feedback loaded",service.feedback(tenantId,auth));
 }
 @PostMapping("/feedback/{feedbackId}/reply")
 public ApiResponse<Map<String,Object>> reply(@PathVariable UUID tenantId,@PathVariable UUID feedbackId,
  @Valid @RequestBody ReplyRequest req,Authentication auth){
  return ApiResponse.success("Reply saved",service.reply(tenantId,feedbackId,req,auth));
 }
 @GetMapping("/promotions")
 public ApiResponse<List<Map<String,Object>>> promotions(@PathVariable UUID tenantId,Authentication auth){
  return ApiResponse.success("Promotions loaded",service.promotions(tenantId,auth));
 }
 @PostMapping("/promotions")
 public ApiResponse<Map<String,Object>> createPromotion(@PathVariable UUID tenantId,
  @Valid @RequestBody PromotionRequest req,Authentication auth){
  return ApiResponse.success("Promotion created",service.createPromotion(tenantId,req,auth));
 }
 @PatchMapping("/promotions/{promotionId}")
 public ApiResponse<Map<String,Object>> updatePromotion(@PathVariable UUID tenantId,@PathVariable UUID promotionId,
  @Valid @RequestBody PromotionStatusRequest req,Authentication auth){
  return ApiResponse.success("Promotion updated",service.updatePromotion(tenantId,promotionId,req,auth));
 }
 @GetMapping("/customers/analytics")
 public ApiResponse<List<Map<String,Object>>> customers(@PathVariable UUID tenantId,Authentication auth){
  return ApiResponse.success("No identified customer profiles yet",service.customers(tenantId,auth));
 }
}
