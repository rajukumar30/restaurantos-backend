package com.restaurantos.modules.staff.service;
import com.restaurantos.modules.staff.dto.StaffOperationsDto.KitchenStatusRequest;
import org.springframework.security.core.Authentication;
import java.util.*;
public interface StaffOperationsService {
 List<Map<String,Object>> branches(UUID tenantId,Authentication auth);
 Map<String,Object> access(UUID tenantId,UUID branchId,Authentication auth);
 Map<String,Object> serviceMode(UUID tenantId,UUID branchId,Authentication auth);
 Map<String,Object> kitchenServed(UUID tenantId,UUID orderId,KitchenStatusRequest request,Authentication auth);
 Map<String,Object> kitchenStatus(UUID tenantId,UUID orderId,KitchenStatusRequest request,Authentication auth);
}
