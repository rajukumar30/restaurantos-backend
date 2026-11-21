package com.restaurantos.modules.orders.dto;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
public final class TableBillDto {
 private TableBillDto(){}
 public record BillSummary(UUID visitId,String billNumber,String status,String tableName,
  Instant openedAt,BigDecimal totalAmount,int orderCount){}
 public record BillLine(String orderNumber,String itemName,int quantity,
  BigDecimal unitPrice,BigDecimal lineTotal){}
 public record BillDetail(BillSummary summary,List<BillLine> items){}
}
