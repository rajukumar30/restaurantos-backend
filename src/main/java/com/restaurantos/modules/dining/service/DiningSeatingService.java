package com.restaurantos.modules.dining.service;
import com.restaurantos.modules.dining.dto.DiningDto.*;
import com.restaurantos.modules.floor.dto.FloorDto.FloorAction;
import com.restaurantos.modules.floor.service.FloorService;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class DiningSeatingService {
 private final FloorService floor;
 private final DiningSessionService dining;
 public DiningSeatingService(FloorService floor,DiningSessionService dining){this.floor=floor;this.dining=dining;}
 /** Seating, code generation and after-commit floor events share one transaction. */
 @Transactional
 public Seated seat(UUID tenant,UUID table,SeatRequest request,Authentication auth){
  var updated=floor.action(tenant,table,new FloorAction(request.branchId(),"SEAT",null,request.guests()),auth);
  if(updated.visitId()==null)throw new IllegalStateException("Seating did not create a visit");
  var code=dining.issue(tenant,table,new CodeRequest(request.branchId(),updated.visitId()),auth);
  return new Seated(updated,code);
 }
}
