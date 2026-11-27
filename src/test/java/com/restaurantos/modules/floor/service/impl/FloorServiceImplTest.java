package com.restaurantos.modules.floor.service.impl;
import com.restaurantos.modules.floor.dto.FloorDto.*;
import com.restaurantos.modules.floor.repository.FloorDao;
import com.restaurantos.modules.staff.TenantAccessService;
import com.restaurantos.modules.platform.TenantFeatureAccess;
import com.restaurantos.modules.realtime.service.RealtimeEventPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.*;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class FloorServiceImplTest{
 private final FloorDao dao=mock(FloorDao.class);
 private final TenantAccessService access=mock(TenantAccessService.class);
 private final TenantFeatureAccess features=mock(TenantFeatureAccess.class);
 private final RealtimeEventPublisher events=mock(RealtimeEventPublisher.class);
 private final Authentication auth=mock(Authentication.class);
 private final FloorServiceImpl service=new FloorServiceImpl(dao,access,features,events);
 private final UUID tenant=UUID.randomUUID(),branch=UUID.randomUUID(),table=UUID.randomUUID(),visit=UUID.randomUUID(),actor=UUID.randomUUID();
 private TableState state(String status,Instant grace,long served,BigDecimal bill){
  return new TableState(table,"01","Table 01",4,true,status==null?null:visit,status,2,Instant.now().minusSeconds(600),grace,null,0,0,0,served,bill,0,false,false,null,null,null);
 }
 private void allow(TableState s){when(access.requirePermission(auth,tenant,branch,"floor:manage")).thenReturn(actor);when(dao.lock(tenant,branch,table)).thenReturn(true);when(dao.one(tenant,branch,table)).thenReturn(Optional.of(s));}
 @Test void cannotReleasePaidTableBeforeGraceEnds(){allow(state("PAID",Instant.now().plusSeconds(180),1,BigDecimal.TEN));assertThrows(ResponseStatusException.class,()->service.action(tenant,table,new FloorAction(branch,"COMPLETE_CLEANING",visit,null),auth));verify(dao,never()).close(any(),any(),any(),any());}
 @Test void cleaningCompletesWithoutSeparateDepartureAction(){allow(state("PAID",Instant.now().minusSeconds(1),1,BigDecimal.TEN));service.action(tenant,table,new FloorAction(branch,"COMPLETE_CLEANING",visit,null),auth);verify(dao).close(tenant,branch,table,visit);verify(dao).audit(tenant,branch,table,visit,actor,"COMPLETE_CLEANING");verify(events).publishFloorAfterCommit(any());}
 @Test void cannotCloseUnpaidVisit(){allow(state("OPEN",null,1,BigDecimal.TEN));assertThrows(ResponseStatusException.class,()->service.action(tenant,table,new FloorAction(branch,"COMPLETE_CLEANING",visit,null),auth));assertThrows(ResponseStatusException.class,()->service.action(tenant,table,new FloorAction(branch,"CLEAN_EMPTY_VISIT",visit,null),auth));verify(dao,never()).close(any(),any(),any(),any());}
 @Test void staleVisitCannotMutateNextGuests(){allow(state("OPEN",null,0,BigDecimal.ZERO));assertThrows(ResponseStatusException.class,()->service.action(tenant,table,new FloorAction(branch,"UPDATE_GUESTS",UUID.randomUUID(),2),auth));verify(dao,never()).guests(any(),anyInt());}
 @Test void seatingRequiresCapacityAndPermission(){allow(state(null,null,0,BigDecimal.ZERO));assertThrows(ResponseStatusException.class,()->service.action(tenant,table,new FloorAction(branch,"SEAT",null,5),auth));verify(dao,never()).seat(any(),any(),any(),any(),anyInt());verify(access).requirePermission(auth,tenant,branch,"floor:manage");}
}
