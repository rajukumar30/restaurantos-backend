package com.restaurantos.modules.staff.service;
import com.restaurantos.modules.staff.dto.ServiceModeDto.ServiceModeRequest;
import org.springframework.security.core.Authentication;
import java.util.*;
public interface PlatformServiceModeService {
 List<Map<String,Object>> list(UUID tenantId,Authentication auth);
 Map<String,Object> set(UUID tenantId,ServiceModeRequest request,Authentication auth);
}
