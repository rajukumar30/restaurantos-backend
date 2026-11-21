package com.restaurantos.modules.tables.service;
import com.restaurantos.modules.tables.dto.TableDto.*;
import org.springframework.security.core.Authentication;
import java.util.*;
public interface TableManagementService {
 List<RestaurantTable> list(UUID tenantId,UUID branchId,Authentication authentication);
 RestaurantTable create(UUID tenantId,CreateTableRequest request,Authentication authentication);
 RestaurantTable update(UUID tenantId,UUID tableId,UpdateTableRequest request,Authentication authentication);
 PublicTableMenu publicMenu(UUID tenantId,String qrToken);
}
