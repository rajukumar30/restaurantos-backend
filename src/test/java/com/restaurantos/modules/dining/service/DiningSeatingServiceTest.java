package com.restaurantos.modules.dining.service;
import com.restaurantos.modules.dining.dto.DiningDto.*;
import com.restaurantos.modules.floor.dto.FloorDto.*;
import com.restaurantos.modules.floor.service.FloorService;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class DiningSeatingServiceTest {
 private final FloorService floor=mock(FloorService.class);
 private final DiningSessionService dining=mock(DiningSessionService.class);
 private final DiningSeatingService service=new DiningSeatingService(floor,dining);
 private final UUID tenant=UUID.randomUUID(),branch=UUID.randomUUID(),table=UUID.randomUUID(),visit=UUID.randomUUID();
 private final Authentication auth=mock(Authentication.class);
 @Test void seatingReturnsCodeForExactlyTheCreatedVisit(){
  var state=new TableState(table,"01","Table 01",4,true,visit,"OPEN",2,Instant.now(),null,null,0,0,0,0,BigDecimal.ZERO,0,false,false,null,null,null);
  var code=new Code("123456",visit,Instant.now().plusSeconds(60));
  when(floor.action(tenant,table,new FloorAction(branch,"SEAT",null,2),auth)).thenReturn(state);
  when(dining.issue(tenant,table,new CodeRequest(branch,visit),auth)).thenReturn(code);
  var result=service.seat(tenant,table,new SeatRequest(branch,2),auth);
  assertEquals(visit,result.table().visitId());assertEquals(code,result.diningCode());
 }
 @Test void seatingFailureCannotIssueCode(){
  when(floor.action(any(),any(),any(),any())).thenThrow(new IllegalStateException("Table occupied"));
  assertThrows(IllegalStateException.class,()->service.seat(tenant,table,new SeatRequest(branch,2),auth));verifyNoInteractions(dining);
 }
 @Test void serviceUsesTransaction()throws Exception{assertNotNull(DiningSeatingService.class.getMethod("seat",UUID.class,UUID.class,SeatRequest.class,Authentication.class).getAnnotation(Transactional.class));}
}
