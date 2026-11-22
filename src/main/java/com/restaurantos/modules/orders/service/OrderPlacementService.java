package com.restaurantos.modules.orders.service;
import com.restaurantos.modules.orders.dto.OrderDto.*;
public interface OrderPlacementService {OrderReceipt place(String qrToken,PlaceOrderRequest request,String idempotencyKey);}
