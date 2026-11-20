package com.restaurantos.modules.staff;

import com.restaurantos.common.api.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@RestController
@RequestMapping("/api/v1/platform/restaurant-service")
public class PlatformServiceModeController {
 private final JdbcTemplate jdbc;
 public PlatformServiceModeController(JdbcTemplate jdbc){this.jdbc=jdbc;}
 private void requireAdmin(Authentication auth){
  if(auth==null||auth.getAuthorities()==null||auth.getAuthorities().stream().noneMatch(a->"PLATFORM_ADMIN".equals(a.getAuthority())))
   throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Platform administrator required");
 }
 public record ServiceModeRequest(@NotNull UUID branchId,@NotNull String mode){}
 @GetMapping("/{tenantId}")
 public ApiResponse<List<Map<String,Object>>> list(@PathVariable UUID tenantId,Authentication auth){
  requireAdmin(auth);
  return ApiResponse.success("Branch service modes",jdbc.queryForList("""
   SELECT b.id AS "branchId",b.name AS "branchName",
   COALESCE(s.service_mode,'WAITER_CONFIRMED') AS "mode"
   FROM branches b LEFT JOIN restaurant_branch_service_settings s
    ON s.tenant_id=b.tenant_id AND s.branch_id=b.id
   WHERE b.tenant_id=? AND b.status='ACTIVE' ORDER BY b.name
   """,tenantId));
 }
 @PutMapping("/{tenantId}")
 @Transactional
 public ApiResponse<Map<String,Object>> set(@PathVariable UUID tenantId,@Valid @RequestBody ServiceModeRequest request,Authentication auth){
  requireAdmin(auth);
  String mode=request.mode().trim().toUpperCase(Locale.ROOT);
  if(!List.of("KITCHEN_MANAGED","WAITER_CONFIRMED").contains(mode))
   throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Unsupported service mode");
  Integer exists=jdbc.queryForObject("SELECT COUNT(*) FROM branches WHERE tenant_id=? AND id=? AND status='ACTIVE'",Integer.class,tenantId,request.branchId());
  if(exists==null||exists==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Branch not found");
  jdbc.update("""
   INSERT INTO restaurant_branch_service_settings(tenant_id,branch_id,service_mode)
   VALUES(?,?,?)
   ON CONFLICT (tenant_id,branch_id) DO UPDATE SET service_mode=EXCLUDED.service_mode
   """,tenantId,request.branchId(),mode);
  return ApiResponse.success("Service mode updated",Map.of("branchId",request.branchId(),"mode",mode));
 }
}
