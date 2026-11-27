package com.restaurantos.modules.tables;
import com.restaurantos.common.api.ApiResponse;
import com.restaurantos.modules.tables.dto.TableDto.*;
import com.restaurantos.modules.tables.service.TableManagementService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/tables")
public class TableController {
 private final TableManagementService service;
 public TableController(TableManagementService service){this.service=service;}
 @GetMapping
 public ApiResponse<List<RestaurantTable>> list(@PathVariable UUID tenantId,@RequestParam UUID branchId,Authentication auth){
  return ApiResponse.success("Tables loaded",service.list(tenantId,branchId,auth));
 }
 @PostMapping
 public ApiResponse<RestaurantTable> create(@PathVariable UUID tenantId,@Valid @RequestBody CreateTableRequest request,Authentication auth){
  return ApiResponse.success("Table created",service.create(tenantId,request,auth));
 }
 @PatchMapping("/{tableId}")
 public ApiResponse<RestaurantTable> update(@PathVariable UUID tenantId,@PathVariable UUID tableId,@Valid @RequestBody UpdateTableRequest request,Authentication auth){
  return ApiResponse.success("Table updated",service.update(tenantId,tableId,request,auth));
 }
 @GetMapping("/public/qr/{qrToken}")
 public ApiResponse<PublicTableMenu> publicMenu(@PathVariable UUID tenantId,@PathVariable String qrToken){
  return ApiResponse.success("Public menu loaded",service.publicMenu(tenantId,qrToken));
 }
}
