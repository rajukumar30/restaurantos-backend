package com.restaurantos.modules.dining;
import com.restaurantos.common.api.ApiResponse;
import com.restaurantos.modules.dining.dto.DiningDto.*;
import com.restaurantos.modules.dining.service.DiningSessionService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController
public class DiningController{
 private final DiningSessionService service;public DiningController(DiningSessionService service){this.service=service;}
 @PostMapping("/api/v1/tenants/{tenant}/floor/{table}/dining-code")
 public ApiResponse<Code> issue(@PathVariable UUID tenant,@PathVariable UUID table,@Valid @RequestBody CodeRequest r,Authentication auth){return ApiResponse.success("Dining code created",service.issue(tenant,table,r,auth));}
 @PostMapping("/api/v1/public/qr/{qr}/dining-session")
 public ApiResponse<Session> join(@PathVariable String qr,@Valid @RequestBody JoinRequest r){return ApiResponse.success("Dining session joined",service.join(qr,r.code()));}
 @Transactional
 @GetMapping("/api/v1/public/qr/{qr}/dining-session")
 public ApiResponse<Map<String,Object>> status(@PathVariable String qr,@RequestHeader(value="X-Dining-Session",required=false) String token){var v=service.require(qr,token,false,false);return ApiResponse.success("Dining session active",Map.of("visitId",v.scope().visit(),"status",v.status()));}
}
