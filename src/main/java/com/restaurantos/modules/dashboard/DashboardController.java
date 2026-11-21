package com.restaurantos.modules.dashboard;
import com.restaurantos.common.api.ApiResponse;
import com.restaurantos.modules.dashboard.dto.DashboardData;
import com.restaurantos.modules.dashboard.service.DashboardService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/dashboard")
public class DashboardController {
 private final DashboardService service;
 public DashboardController(DashboardService service){this.service=service;}
 @GetMapping
 public ApiResponse<DashboardData> dashboard(@PathVariable UUID tenantId,@RequestParam UUID branchId,Authentication authentication){
  return ApiResponse.success("Dashboard loaded",service.load(tenantId,branchId,authentication));
 }
}
