package com.restaurantos.modules.floor.dto;
import java.time.Instant;
import java.util.*;
import java.math.BigDecimal;
import jakarta.validation.constraints.*;
public final class FloorDto {
 private FloorDto(){}
 public record TableState(UUID tableId,String number,String name,int capacity,boolean active,UUID visitId,String visitStatus,
 Integer guests,Instant openedAt,Instant graceUntil,Instant cleaningStartedAt,long newOrders,long preparing,long ready,long served,
 BigDecimal bill,long requests,boolean billRequested,boolean cleaningRequested,Instant oldestRequest,Instant oldestNew,Instant oldestReady){}
 public record FloorSnapshot(Instant serverTime,boolean canManage,List<TableState> tables){}
 public record FloorAction(@NotNull UUID branchId,@NotBlank String action,UUID visitId,@Min(1) @Max(50) Integer guests){}
 public record FloorOrder(UUID id,String number,String status,BigDecimal amount,Instant createdAt,String items){}
 public record FloorRequest(UUID id,String type,String status,Instant createdAt){}
 public record FloorDetail(TableState table,List<FloorOrder> orders,List<FloorRequest> requests){}
}
