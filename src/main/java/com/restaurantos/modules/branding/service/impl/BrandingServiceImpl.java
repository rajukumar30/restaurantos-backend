package com.restaurantos.modules.branding.service.impl;
import com.restaurantos.modules.branding.dto.BrandingSettings;
import com.restaurantos.modules.branding.repository.BrandingDao;
import com.restaurantos.modules.branding.service.BrandingService;
import com.restaurantos.modules.platform.TenantFeatureAccess;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.net.URI;
import java.util.UUID;
@Service
public class BrandingServiceImpl implements BrandingService {
 private final BrandingDao dao;
 private final TenantFeatureAccess features;
 public BrandingServiceImpl(BrandingDao dao,TenantFeatureAccess features){this.dao=dao;this.features=features;}
 private void authorize(UUID tenantId,Authentication auth){
  if(auth==null||!(auth.getPrincipal() instanceof UUID userId))
   throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Sign in required.");
  if(!dao.canManage(tenantId,userId))
   throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Restaurant-wide owner or manager access is required to customize the guest website.");
  features.require(tenantId,"QR_MENU");
 }
 @Transactional(readOnly=true)
 public BrandingSettings get(UUID tenantId,Authentication auth){authorize(tenantId,auth);return dao.get(tenantId);}
 @Transactional
 public BrandingSettings publish(UUID tenantId,BrandingSettings settings,Authentication auth){
  authorize(tenantId,auth);
  validateImage(settings.logoUrl());validateImage(settings.bannerUrl());
  dao.publish(tenantId,settings);
  return settings;
 }
 private void validateImage(String value){
  if(value.isEmpty())return;
  try{
   URI uri=URI.create(value);
   if("https".equalsIgnoreCase(uri.getScheme())&&uri.getHost()!=null&&uri.getUserInfo()==null&&uri.getFragment()==null)return;
  }catch(IllegalArgumentException ignored){}
  throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Images must use a valid HTTPS image URL.");
 }
}
