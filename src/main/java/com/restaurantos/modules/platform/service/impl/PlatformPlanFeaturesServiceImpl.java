package com.restaurantos.modules.platform.service.impl;
import com.restaurantos.modules.platform.dto.PlatformPlanFeatureDto.*;
import com.restaurantos.modules.platform.repository.PlatformPlanFeaturesDao;
import com.restaurantos.modules.platform.service.PlatformPlanFeaturesService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
@Service
public class PlatformPlanFeaturesServiceImpl implements PlatformPlanFeaturesService {
 private final PlatformPlanFeaturesDao dao;
 public PlatformPlanFeaturesServiceImpl(PlatformPlanFeaturesDao dao){this.dao=dao;}
 @Transactional(readOnly=true)
 public List<PlanFeature> list(UUID id){
  if(dao.status(id,false).isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Plan not found");
  return dao.load(id);
 }
 @Transactional
 public List<PlanFeature> update(UUID id,String key,Update request,Authentication auth){
  if(auth==null||!(auth.getPrincipal() instanceof UUID))
   throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Authentication required");
  List<String> locked=dao.status(id,true);
  if(locked.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Plan not found");
  if(!"DRAFT".equals(locked.getFirst()))
   throw new ResponseStatusException(HttpStatus.CONFLICT,"Only draft plan features can be edited");
  if(request==null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Feature selection required");
  List<PlanFeature> features=dao.load(id);
  PlanFeature selected=features.stream().filter(f->f.key().equals(key)).findFirst()
   .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Unknown feature"));
  if(request.included()&&!selected.available())
   throw new ResponseStatusException(HttpStatus.CONFLICT,"Feature is not available");
  Map<String,Boolean> proposed=new HashMap<>();
  for(PlanFeature f:features)proposed.put(f.key(),f.included());
  proposed.put(key,request.included());
  for(PlanFeature f:features){
   if(!proposed.get(f.key()))continue;
   for(String dependency:f.dependencies())
    if(!Boolean.TRUE.equals(proposed.get(dependency)))
     throw new ResponseStatusException(HttpStatus.CONFLICT,f.key()+" requires "+dependency);
  }
  dao.set(id,key,request.included());
  return dao.load(id);
 }
}
