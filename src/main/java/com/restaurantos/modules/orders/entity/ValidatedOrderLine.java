package com.restaurantos.modules.orders.entity;
import java.math.BigDecimal;
import java.util.UUID;
public record ValidatedOrderLine(UUID menuItemId,String itemName,BigDecimal unitPrice,int quantity){}
