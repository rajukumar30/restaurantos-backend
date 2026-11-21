package com.restaurantos.modules.platform.service.impl;
import com.restaurantos.modules.platform.dto.PlatformFeatureGrantDto.*;
import com.restaurantos.modules.platform.repository.PlatformFeatureGrantDao;
import com.restaurantos.modules.platform.service.PlatformFeatureGrantService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;
@Service
public class PlatformFeatureGrantServiceImpl implements PlatformFeatureGrantService {
 private final PlatformFeatureGrantDao dao;
 public PlatformFeatureGrantServiceImpl(PlatformFeatureGrantDao dao){this.dao=dao;}
 private UUID actor(Authentication auth){
  if(auth==null || !(auth.getPrincipal() instanceof UUID id))
   throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Authentication required");
  return id;
 }
 @Transactional(readOnly=true)
 public List<Grant> list(UUID tenant){
  if(!dao.activeTenant(tenant))throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Active restaurant not found");
  return dao.list(tenant);
 }
 @Transactional
 public List<Grant> create(UUID tenant,Create req,Authentication auth){
  UUID user=actor(auth);
  if(req==null || req.featureKey()==null || !req.featureKey().matches("[A-Z][A-Z0-9_]{0,79}") || req.expiresAt()==null)
   throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Valid feature and expiry required");
  OffsetDateTime now=OffsetDateTime.now(ZoneOffset.UTC);
  if(!req.expiresAt().isAfter(now) || req.expiresAt().isAfter(now.plusYears(2)))
   throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Expiry must be within the next two years");
  dao.lockTenant(tenant);
  if(!dao.activeTenant(tenant))throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Active restaurant not found");
  if(!dao.availableFeature(req.featureKey()))throw new ResponseStatusException(HttpStatus.CONFLICT,"Feature unavailable");
  dao.insert(tenant,req.featureKey(),req.expiresAt(),user);
  dao.audit(user,tenant,"FEATURE_GRANT_CREATED",req.featureKey());
  return dao.list(tenant);
 }
 @Transactional
 public List<Grant> revoke(UUID tenant,UUID grant,Authentication auth){
  UUID user=actor(auth);
  dao.lockTenant(tenant);
  String feature=dao.featureOf(tenant,grant);
  if(feature==null || !dao.revoke(tenant,grant))
   throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Active grant not found");
  dao.audit(user,tenant,"FEATURE_GRANT_REVOKED",feature);
  return dao.list(tenant);
 }
}
