package com.restaurantos.modules.platform.service.impl;
import com.restaurantos.modules.platform.dto.PlatformEntitlementsDto.*;
import com.restaurantos.modules.platform.repository.PlatformEntitlementsDao;
import com.restaurantos.modules.platform.service.PlatformEntitlementsService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;
@Service
public class PlatformEntitlementsServiceImpl implements PlatformEntitlementsService {
 private final PlatformEntitlementsDao dao;
 private final int graceDays;
 public PlatformEntitlementsServiceImpl(PlatformEntitlementsDao dao,@Value("${restaurantos.entitlements.grace-days:7}") int graceDays){
  this.dao=dao;this.graceDays=Math.max(0,Math.min(90,graceDays));
 }
 @Transactional(readOnly=true)
 public Snapshot snapshot(UUID tenantId){
  if(!dao.tenantExists(tenantId))
   throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Restaurant not found");
  LocalDate now=LocalDate.now(ZoneOffset.UTC);
  List<Map<String,Object>> subscriptions=dao.subscriptions(tenantId);
  UUID planId=null;String planName=null;String state="UNASSIGNED";boolean eligible=false;
  if(!subscriptions.isEmpty()){
   Map<String,Object> s=subscriptions.getFirst();
   planId=(UUID)s.get("plan_id");planName=(String)s.get("plan_name");
   LocalDate start=(LocalDate)s.get("starts_on");
   LocalDate end=(LocalDate)s.get("ends_on");
   state=now.isBefore(start)?"SCHEDULED":end!=null&&now.isAfter(end.plusDays(graceDays))?"EXPIRED":end!=null&&now.isAfter(end)?"GRACE_PERIOD":"ACTIVE";
   eligible="ACTIVE".equals(state)||"GRACE_PERIOD".equals(state);
  }
  boolean active=eligible,assigned=planId!=null;
  List<Entitlement> features=dao.features(tenantId,planId).stream().map(f->{
   boolean effective=f.available()&&f.enabled()&&((active&&f.included())||f.grantActive());
   String reason=effective&&f.grantActive()&&!f.included()?"COMPLIMENTARY_GRANT":!assigned?"NO_SUBSCRIPTION":!active?"SUBSCRIPTION_INACTIVE":!f.available()?"PLATFORM_UNAVAILABLE":!f.included()?"NOT_IN_PLAN":!f.enabled()?"RESTAURANT_DISABLED":"ENABLED";
   return new Entitlement(f.key(),f.name(),f.category(),f.available(),f.included(),f.enabled(),effective,reason);
  }).toList();
  return new Snapshot(tenantId,planId,planName,state,eligible,features);
 }
}
