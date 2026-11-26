package com.restaurantos.modules.orders.service;
import com.restaurantos.modules.orders.dto.OrderDto.*;
import org.springframework.security.core.Authentication;
import java.util.*;
public interface OrderQueryService {
 PublicOrderStatus publicStatus(String qrToken,UUID orderId);
 List<OrderSummary> list(UUID tenantId,UUID branchId,Authentication auth);
 List<OrderSummary> listLive(UUID tenantId,UUID branchId,Authentication auth);
 OrderWorkspacePage workspace(UUID tenantId,OrderWorkspaceQuery query,Authentication auth);
 OrderSummary get(UUID tenantId,UUID branchId,UUID orderId,Authentication auth);
 OrderStatusUpdated updateStatus(UUID tenantId,UUID orderId,UpdateOrderStatusRequest request,Authentication auth);
}
