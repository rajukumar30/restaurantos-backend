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
   state=now.isBefore(start)?"SCHEDULED":end!=null&&now.isAfter(end)?"EXPIRED":"ACTIVE";
   eligible="ACTIVE".equals(state);
  }
  boolean active=eligible,assigned=planId!=null;
  List<Entitlement> features=dao.features(tenantId,planId).stream().map(f->{
   boolean effective=f.available()&&f.enabled()&&active&&(f.included()||f.grantActive());
   String reason=effective&&f.grantActive()&&!f.included()?"COMPLIMENTARY_GRANT":!f.available()?"PLATFORM_UNAVAILABLE":!f.enabled()?"RESTAURANT_DISABLED":!assigned?"NO_SUBSCRIPTION":!active?"SUBSCRIPTION_INACTIVE":!f.included()&&!f.grantActive()?"NOT_IN_PLAN":"ENABLED";
   return new Entitlement(f.key(),f.name(),f.category(),f.available(),f.included(),f.enabled(),effective,reason);
  }).toList();
  Map<String,Entitlement> byKey=new HashMap<>();
  for(Entitlement feature:features)byKey.put(feature.key(),feature);
  Map<String,List<String>> dependencies=Map.of(
   "GUEST_ORDERING",List.of("QR_MENU"),"KITCHEN_DISPLAY",List.of("GUEST_ORDERING"),
   "WAITER_DASHBOARD",List.of("GUEST_ORDERING"),"ORDER_TRACKING",List.of("GUEST_ORDERING"),
   "ORDER_HISTORY",List.of("GUEST_ORDERING"),"SERVICE_REQUESTS",List.of("QR_MENU"),
   "DINE_IN",List.of("GUEST_ORDERING"),"TAKEAWAY",List.of("GUEST_ORDERING"));
  List<Entitlement> resolved=features.stream().map(feature->{
   if(!feature.effectiveEnabled())return feature;
   boolean ready=dependencies.getOrDefault(feature.key(),List.of()).stream()
    .allMatch(dep->dependencyEnabled(dep,byKey,dependencies,new HashSet<>()));
   if(ready)return feature;
   return new Entitlement(feature.key(),feature.name(),feature.category(),
    feature.platformAvailable(),feature.planIncluded(),feature.restaurantEnabled(),false,"DEPENDENCY_DISABLED");
  }).toList();
  return new Snapshot(tenantId,planId,planName,state,eligible,resolved);
 }
 private boolean dependencyEnabled(String key,Map<String,Entitlement> features,
   Map<String,List<String>> dependencies,Set<String> visited){
  if(!visited.add(key))return false;
  Entitlement entitlement=features.get(key);
  if(entitlement==null||!entitlement.effectiveEnabled())return false;
  return dependencies.getOrDefault(key,List.of()).stream()
   .allMatch(dep->dependencyEnabled(dep,features,dependencies,new HashSet<>(visited)));
 }

}
