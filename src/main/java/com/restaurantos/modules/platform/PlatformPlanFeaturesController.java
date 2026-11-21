package com.restaurantos.modules.platform;
import com.restaurantos.common.api.ApiResponse;
import com.restaurantos.modules.platform.dto.PlatformPlanFeatureDto.*;
import com.restaurantos.modules.platform.service.PlatformPlanFeaturesService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController
@RequestMapping("/api/v1/platform/plans")
public class PlatformPlanFeaturesController {
 private final PlatformPlanFeaturesService service;
 public PlatformPlanFeaturesController(PlatformPlanFeaturesService service){this.service=service;}
 @GetMapping("/{id}/features")
 public ApiResponse<List<PlanFeature>> list(@PathVariable UUID id){
  return ApiResponse.success("Plan feature configuration",service.list(id));
 }
 @PutMapping("/{id}/features/{key}")
 public ApiResponse<List<PlanFeature>> update(@PathVariable UUID id,@PathVariable String key,
  @RequestBody Update request,Authentication authentication){
  return ApiResponse.success("Draft plan features updated",service.update(id,key,request,authentication));
 }
}
