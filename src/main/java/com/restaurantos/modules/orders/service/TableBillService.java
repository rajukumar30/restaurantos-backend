package com.restaurantos.modules.orders.service;
import com.restaurantos.modules.orders.dto.TableBillDto.*;
import org.springframework.security.core.Authentication;
import java.util.*;
public interface TableBillService {
 List<BillSummary> active(UUID tenantId,UUID branchId,Authentication auth);
 BillDetail detail(UUID tenantId,UUID branchId,UUID visitId,Authentication auth);
}
