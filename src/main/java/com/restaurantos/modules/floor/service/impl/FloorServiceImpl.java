package com.restaurantos.modules.floor.service.impl;
import com.restaurantos.modules.floor.dto.FloorDto.*;
import com.restaurantos.modules.floor.repository.FloorDao;
import com.restaurantos.modules.floor.service.FloorService;
import com.restaurantos.modules.staff.TenantAccessService;
import com.restaurantos.modules.platform.TenantFeatureAccess;
import com.restaurantos.modules.realtime.dto.RealtimeEvent;
import com.restaurantos.modules.realtime.service.RealtimeEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.Authentication;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.*;
@Service
public class FloorServiceImpl implements FloorService{
 private final FloorDao dao;private final TenantAccessService access;private final TenantFeatureAccess features;private final RealtimeEventPublisher events;
 public FloorServiceImpl(FloorDao dao,TenantAccessService access,TenantFeatureAccess features,RealtimeEventPublisher events){this.dao=dao;this.access=access;this.features=features;this.events=events;}
 private UUID auth(UUID tenant,UUID branch,Authentication auth,String permission){UUID actor=access.requirePermission(auth,tenant,branch,permission);access.requireBranchInTenant(tenant,branch);features.require(tenant,"GUEST_ORDERING");return actor;}
 private TableState require(UUID tenant,UUID branch,UUID table){return dao.one(tenant,branch,table).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Table not found"));}
 @Transactional(readOnly=true)
 public FloorSnapshot snapshot(UUID tenant,UUID branch,Authentication auth){auth(tenant,branch,auth,"floor:read");boolean manage=true;try{access.requirePermission(auth,tenant,branch,"floor:manage");}catch(ResponseStatusException e){if(e.getStatusCode()!=HttpStatus.FORBIDDEN)throw e;manage=false;}return new FloorSnapshot(Instant.now(),manage,dao.list(tenant,branch));}
 @Transactional(readOnly=true)
 public FloorDetail detail(UUID tenant,UUID branch,UUID table,Authentication auth){auth(tenant,branch,auth,"floor:read");var t=require(tenant,branch,table);return new FloorDetail(t,dao.orders(tenant,branch,t.visitId()),dao.requests(tenant,branch,table));}
 @Transactional
 public TableState action(UUID tenant,UUID table,FloorAction req,Authentication auth){
  UUID branch=req.branchId(),actor=auth(tenant,branch,auth,"floor:manage");
  if(!dao.lock(tenant,branch,table))throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Table not found");
  var t=require(tenant,branch,table);UUID visit=t.visitId();
  if(!Objects.equals(visit,req.visitId()))throw conflict("Table visit changed. Refresh and try again.");
  String action=req.action();
  if("SEAT".equals(action)){
   if(!t.active()||visit!=null)throw conflict("Table is not available");
   checkGuests(t,req.guests());visit=UUID.randomUUID();dao.seat(tenant,branch,table,visit,req.guests());
  }else{
   if(visit==null)throw conflict("No active table visit");
   boolean paid="PAID".equals(t.visitStatus()),due=t.graceUntil()!=null&&!Instant.now().isBefore(t.graceUntil());
   switch(action){
    case "UPDATE_GUESTS" -> {if(!"OPEN".equals(t.visitStatus()))throw conflict("Visit is being settled");checkGuests(t,req.guests());dao.guests(visit,req.guests());}
    case "EXTEND_GRACE" -> {if(!paid||t.cleaningStartedAt()!=null)throw conflict("Only a paid table awaiting cleaning can be extended");dao.extend(visit);}
    case "START_CLEANING" -> {if(!paid||!due||t.cleaningStartedAt()!=null)throw conflict("Wait until payment grace period ends");dao.start(visit);}
    case "COMPLETE_CLEANING" -> {if(!paid||!due)throw conflict("Payment and grace period must be complete");dao.close(tenant,branch,table,visit);}
    case "CLEAN_EMPTY_VISIT" -> {if(!"OPEN".equals(t.visitStatus())||t.newOrders()+t.preparing()+t.ready()+t.served()!=0||t.bill().signum()!=0)throw conflict("Only an empty or cancelled visit can be released");dao.close(tenant,branch,table,visit);}
    default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Unsupported floor action");
   }
  }
  dao.audit(tenant,branch,table,visit,actor,action);
  events.publishFloorAfterCommit(RealtimeEvent.orderChanged("FLOOR_CHANGED",tenant,branch,table));
  return require(tenant,branch,table);
 }
 private void checkGuests(TableState t,Integer guests){if(guests==null||guests<1||guests>t.capacity())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Guest count must fit table capacity");}
 private ResponseStatusException conflict(String message){return new ResponseStatusException(HttpStatus.CONFLICT,message);}
}
