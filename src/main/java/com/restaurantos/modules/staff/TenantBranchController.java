package com.restaurantos.modules.staff;
import com.restaurantos.common.api.ApiResponse;
import com.restaurantos.modules.staff.dto.TenantBranchDto.*;
import com.restaurantos.modules.staff.service.TenantBranchService;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import java.util.*;
@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/branches")
public class TenantBranchController {
 private final TenantBranchService service;
 public TenantBranchController(TenantBranchService service){this.service=service;}
 @GetMapping
 public ApiResponse<List<BranchSummary>> list(@PathVariable UUID tenantId,Authentication authentication){
  return ApiResponse.success("Branches loaded",service.list(tenantId,authentication));
 }
}
