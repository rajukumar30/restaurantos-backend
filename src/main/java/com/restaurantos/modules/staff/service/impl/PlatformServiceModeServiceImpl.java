package com.restaurantos.modules.staff.service.impl;
import com.restaurantos.modules.staff.dto.ServiceModeDto.ServiceModeRequest;
import com.restaurantos.modules.staff.repository.PlatformServiceModeDao;
import com.restaurantos.modules.staff.service.PlatformServiceModeService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
@Service
public class PlatformServiceModeServiceImpl implements PlatformServiceModeService {
 private final PlatformServiceModeDao dao;
 public PlatformServiceModeServiceImpl(PlatformServiceModeDao dao){this.dao=dao;}
 private void requireAdmin(Authentication auth){
  if(auth==null||auth.getAuthorities()==null||auth.getAuthorities().stream().noneMatch(a->"PLATFORM_ADMIN".equals(a.getAuthority())))
   throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Platform administrator required");
 }
 @Transactional(readOnly=true)
 public List<Map<String,Object>> list(UUID tenantId,Authentication auth){
  requireAdmin(auth);return dao.list(tenantId);
 }
 @Transactional
 public Map<String,Object> set(UUID tenantId,ServiceModeRequest request,Authentication auth){
  requireAdmin(auth);
  String mode=request.mode().trim().toUpperCase(Locale.ROOT);
  if(!List.of("KITCHEN_MANAGED","WAITER_CONFIRMED").contains(mode))
   throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Unsupported service mode");
  if(!dao.activeBranchExists(tenantId,request.branchId()))
   throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Branch not found");
  dao.upsert(tenantId,request.branchId(),mode);
  return Map.of("branchId",request.branchId(),"mode",mode);
 }
}
