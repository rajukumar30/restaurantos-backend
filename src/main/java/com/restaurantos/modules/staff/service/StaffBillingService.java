package com.restaurantos.modules.staff.service;
import com.restaurantos.modules.staff.dto.StaffBillingDto.PaymentRequest;
import org.springframework.security.core.Authentication;
import java.util.*;
public interface StaffBillingService {
 List<Map<String,Object>> bills(UUID tenantId,UUID branchId,Authentication auth);
 Map<String,Object> record(UUID tenantId,UUID orderId,PaymentRequest request,Authentication auth);
}
