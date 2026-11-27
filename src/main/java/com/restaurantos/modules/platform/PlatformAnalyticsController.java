package com.restaurantos.modules.platform;
import com.restaurantos.common.api.ApiResponse;
import com.restaurantos.modules.platform.dto.PlatformAnalyticsDto.*;
import com.restaurantos.modules.platform.service.PlatformAnalyticsService;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import java.util.*;
@RestController
@RequestMapping("/api/v1/platform/analytics")
public class PlatformAnalyticsController {
 private final PlatformAnalyticsService service;
 public PlatformAnalyticsController(PlatformAnalyticsService service){this.service=service;}
 @GetMapping("/overview")
 public ApiResponse<Overview> overview(){
  return ApiResponse.success("Platform overview",service.overview());
 }
}
