package com.restaurantos.modules.service.service;
import com.restaurantos.modules.service.dto.ServiceRequestDto.*;
import org.springframework.security.core.Authentication;
import java.util.*;
public interface ServiceRequestService {
 boolean availability(String qrToken);
 ServiceRequest create(String qrToken,CreateRequest request);
 List<ServiceRequest> guestList(String qrToken);
 ServiceRequest cancel(String qrToken,UUID requestId);
 List<ServiceRequest> staffList(UUID tenantId,UUID branchId,Authentication auth);
 ServiceRequest update(UUID tenantId,UUID requestId,UUID branchId,UpdateRequest request,Authentication auth);
}
