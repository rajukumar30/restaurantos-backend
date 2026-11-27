package com.restaurantos.modules.orders.service;
import com.restaurantos.modules.orders.dto.OrderDto.*;
public interface OrderPlacementService {OrderReceipt place(String qrToken,PlaceOrderRequest request,String idempotencyKey);
 OrderReceipt placeChecked(String qrToken,PlaceOrderRequest request,String idempotencyKey,java.util.UUID visitId,java.util.UUID reservedId,java.math.BigDecimal expectedTotal);
}
