package com.restaurantos.modules.orders;
import com.restaurantos.common.api.ApiResponse;
import com.restaurantos.modules.orders.dto.TableBillDto.*;
import com.restaurantos.modules.orders.service.TableBillService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/bills")
public class TableBillController {
 private final TableBillService service;
 public TableBillController(TableBillService service){this.service=service;}
 @GetMapping("/active")
 public ApiResponse<List<BillSummary>> active(@PathVariable UUID tenantId,@RequestParam UUID branchId,Authentication authentication){
  return ApiResponse.success("Active table bills",service.active(tenantId,branchId,authentication));
 }
 @GetMapping("/{visitId}")
 public ApiResponse<BillDetail> detail(@PathVariable UUID tenantId,@PathVariable UUID visitId,
  @RequestParam UUID branchId,Authentication authentication){
  return ApiResponse.success("Consolidated bill preview",service.detail(tenantId,branchId,visitId,authentication));
 }
}
