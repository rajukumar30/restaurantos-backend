package com.restaurantos.modules.ordersecurity;
import com.restaurantos.common.api.ApiResponse;
import com.restaurantos.modules.ordersecurity.dto.OrderSecurityDto.*;
import com.restaurantos.modules.ordersecurity.service.OrderSecurityService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController
public class OrderSecurityController{
 private final OrderSecurityService service;public OrderSecurityController(OrderSecurityService service){this.service=service;}
 @GetMapping("/api/v1/public/qr/{qr}/ordering-security") public ApiResponse<Access> access(@PathVariable String qr){return ApiResponse.success("Ordering security",service.publicAccess(qr));}
 @PostMapping("/api/v1/public/qr/{qr}/dining-session/express") public ApiResponse<Joined> join(@PathVariable String qr){return ApiResponse.success("Ordering session created",service.join(qr));}
 @GetMapping("/api/v1/public/qr/{qr}/ordering-session") public ApiResponse<Map<String,Object>> status(@PathVariable String qr,@RequestHeader(value="X-Dining-Session",required=false) String token){return ApiResponse.success("Ordering session",service.status(qr,token));}
 @GetMapping("/api/v1/tenants/{tenant}/ordering-security") public ApiResponse<Settings> get(@PathVariable UUID tenant,@RequestParam UUID branchId,Authentication auth){return ApiResponse.success("Ordering settings",service.settings(tenant,branchId,auth));}
 @PutMapping("/api/v1/tenants/{tenant}/ordering-security") public ApiResponse<Settings> put(@PathVariable UUID tenant,@Valid @RequestBody Update r,Authentication auth){return ApiResponse.success("Ordering settings saved",service.update(tenant,r,auth));}
 @GetMapping("/api/v1/tenants/{tenant}/order-reviews") public ApiResponse<List<Review>> reviews(@PathVariable UUID tenant,@RequestParam UUID branchId,Authentication auth){return ApiResponse.success("Pending reviews",service.list(tenant,branchId,auth));}
 @PostMapping("/api/v1/tenants/{tenant}/order-reviews/{id}") public ApiResponse<Map<String,String>> decide(@PathVariable UUID tenant,@PathVariable UUID id,@Valid @RequestBody Decision r,Authentication auth){return ApiResponse.success("Review resolved",service.decide(tenant,id,r,auth));}
}
