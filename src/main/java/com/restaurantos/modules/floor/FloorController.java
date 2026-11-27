package com.restaurantos.modules.floor;
import com.restaurantos.modules.floor.dto.FloorDto.*;
import com.restaurantos.modules.floor.service.FloorService;
import com.restaurantos.common.api.ApiResponse;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import jakarta.validation.Valid;
import java.util.UUID;
@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/floor")
public class FloorController{
 private final FloorService service;
 public FloorController(FloorService service){this.service=service;}
 @GetMapping public ApiResponse<FloorSnapshot> snapshot(@PathVariable UUID tenantId,@RequestParam UUID branchId,Authentication auth){return ApiResponse.success("Floor loaded",service.snapshot(tenantId,branchId,auth));}
 @GetMapping("/{tableId}") public ApiResponse<FloorDetail> detail(@PathVariable UUID tenantId,@PathVariable UUID tableId,@RequestParam UUID branchId,Authentication auth){return ApiResponse.success("Table loaded",service.detail(tenantId,branchId,tableId,auth));}
 @PostMapping("/{tableId}/actions") public ApiResponse<TableState> action(@PathVariable UUID tenantId,@PathVariable UUID tableId,@Valid @RequestBody FloorAction request,Authentication auth){return ApiResponse.success("Table updated",service.action(tenantId,tableId,request,auth));}
}
