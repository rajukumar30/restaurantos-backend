package com.restaurantos.modules.platform;
import com.restaurantos.common.api.ApiResponse;
import com.restaurantos.modules.platform.dto.PlatformAuditDto.*;
import com.restaurantos.modules.platform.service.PlatformAuditService;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import java.util.*;
@RestController
@RequestMapping("/api/v1/platform/audit")
public class PlatformAuditController {
 private final PlatformAuditService service;
 public PlatformAuditController(PlatformAuditService service){this.service=service;}
 @GetMapping
 public ApiResponse<List<AuditEvent>> list(@RequestParam(defaultValue="0") int page,@RequestParam(required=false) UUID tenantId){
  return ApiResponse.success("Platform audit events",service.list(page,tenantId));
 }
}
