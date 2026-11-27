package com.restaurantos.modules.dining.dto;
import com.restaurantos.modules.orders.dto.CheckoutDto.Scope;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;
public final class DiningDto{
 private DiningDto(){}
 public record SeatRequest(@NotNull UUID branchId,@Min(1) @Max(50) Integer guests){}
 public record Seated(com.restaurantos.modules.floor.dto.FloorDto.TableState table,Code diningCode){}
 public record CodeRequest(@NotNull UUID branchId,@NotNull UUID visitId){}
 public record JoinRequest(@NotBlank @Pattern(regexp="[0-9]{6}") String code){}
 public record Code(String code,UUID visitId,Instant expiresAt){}
 public record Session(String token,UUID visitId,Instant expiresAt){}
 public record Visit(Scope scope,UUID sessionId,String status,boolean verified){public Visit(Scope scope,UUID sessionId,String status){this(scope,sessionId,status,true);}}
 public record Active(Scope scope,String status,String hash,Instant expiresAt,int failures){}
}
