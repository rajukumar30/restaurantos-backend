package com.restaurantos.modules.orders;
import com.restaurantos.common.api.ApiResponse;
import com.restaurantos.modules.orders.dto.CheckoutDto.*;
import com.restaurantos.modules.orders.service.CheckoutService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import java.util.UUID;
@RestController
public class CheckoutController{
 private final CheckoutService service;public CheckoutController(CheckoutService service){this.service=service;}
 @GetMapping("/api/v1/public/qr/{qr}/orders/{order}/bill")
 public ApiResponse<Detail> guest(@PathVariable String qr,@PathVariable UUID order){return ApiResponse.success("Bill loaded",service.guest(qr,order,false));}
 @PostMapping("/api/v1/public/qr/{qr}/orders/{order}/bill/checkout")
 public ApiResponse<Detail> beginGuest(@PathVariable String qr,@PathVariable UUID order){return ApiResponse.success("Checkout requested",service.guest(qr,order,true));}
 @GetMapping("/api/v1/tenants/{tenant}/checkout")
 public ApiResponse<Page> page(@PathVariable UUID tenant,@RequestParam UUID branchId,@RequestParam(defaultValue="pending") String filter,@RequestParam(defaultValue="") String search,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size,Authentication auth){return ApiResponse.success("Bills loaded",service.page(tenant,branchId,filter,search,page,size,auth));}
 @GetMapping("/api/v1/tenants/{tenant}/checkout/{visit}")
 public ApiResponse<Detail> detail(@PathVariable UUID tenant,@PathVariable UUID visit,@RequestParam UUID branchId,Authentication auth){return ApiResponse.success("Bill loaded",service.detail(tenant,branchId,visit,auth));}
 @PostMapping("/api/v1/tenants/{tenant}/checkout/{visit}/begin")
 public ApiResponse<Detail> begin(@PathVariable UUID tenant,@PathVariable UUID visit,@Valid @RequestBody VisitAction r,Authentication auth){return ApiResponse.success("Checkout started",service.change(tenant,r.branchId(),visit,true,auth));}
 @PostMapping("/api/v1/tenants/{tenant}/checkout/{visit}/resume")
 public ApiResponse<Detail> resume(@PathVariable UUID tenant,@PathVariable UUID visit,@Valid @RequestBody VisitAction r,Authentication auth){return ApiResponse.success("Ordering resumed",service.change(tenant,r.branchId(),visit,false,auth));}
 @PostMapping("/api/v1/tenants/{tenant}/checkout/{visit}/confirm")
 public ApiResponse<Detail> confirm(@PathVariable UUID tenant,@PathVariable UUID visit,@Valid @RequestBody Confirm r,Authentication auth){return ApiResponse.success("Payment recorded",service.confirm(tenant,visit,r,auth));}
 @GetMapping("/api/v1/tenants/{tenant}/checkout-settings")
 public ApiResponse<Merchant> settings(@PathVariable UUID tenant,@RequestParam UUID branchId,Authentication auth){return ApiResponse.success("Merchant loaded",service.merchant(tenant,branchId,null,auth));}
 @PutMapping("/api/v1/tenants/{tenant}/checkout-settings")
 public ApiResponse<Merchant> update(@PathVariable UUID tenant,@RequestParam UUID branchId,@Valid @RequestBody Merchant r,Authentication auth){return ApiResponse.success("Merchant updated",service.merchant(tenant,branchId,r,auth));}
}
