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
import java.util.Optional;
import org.springframework.web.multipart.MultipartFile;
import com.restaurantos.common.image.ImageOptimizer;
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
   throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Only the restaurant owner can customize the guest website.");
  features.require(tenantId,"QR_MENU");
 }
 @Transactional(readOnly=true)
 public BrandingSettings get(UUID tenantId,Authentication auth){authorize(tenantId,auth);return dao.get(tenantId);}
 @Transactional(readOnly=true)
 public Optional<byte[]> image(UUID id){return dao.publishedImage(id);}
 @Transactional
 public BrandingSettings publish(UUID tenantId,BrandingSettings settings,Authentication auth){
  authorize(tenantId,auth);return save(tenantId,settings,null,null);
 }
 @Transactional
 public BrandingSettings publishWithImages(UUID tenantId,BrandingSettings settings,MultipartFile logo,MultipartFile banner,Authentication auth){
  authorize(tenantId,auth);return save(tenantId,settings,decode(logo,ImageOptimizer.Kind.LOGO),decode(banner,ImageOptimizer.Kind.BANNER));
 }
 private BrandingSettings save(UUID tenantId,BrandingSettings settings,byte[] logo,byte[] banner){
  dao.lockTenant(tenantId);
  String l=logo==null?validateImage(tenantId,"LOGO",settings.logoUrl()):dao.storeImage(tenantId,"LOGO",logo);
  String b=banner==null?validateImage(tenantId,"BANNER",settings.bannerUrl()):dao.storeImage(tenantId,"BANNER",banner);
  BrandingSettings published=settings.withImages(l,b);
  dao.publish(tenantId,published);
  if(!l.startsWith("/api/v1/public/guest-website/images/"))dao.removeImage(tenantId,"LOGO");
  if(!b.startsWith("/api/v1/public/guest-website/images/"))dao.removeImage(tenantId,"BANNER");
  return published;
 }
 private String validateImage(UUID tenantId,String slot,String value){
  if(value.isEmpty())return value;
  String prefix="/api/v1/public/guest-website/images/";
  try{
   if(value.startsWith(prefix)){
    UUID id=UUID.fromString(value.substring(prefix.length()));
    if(dao.ownsImage(tenantId,slot,id))return prefix+id;
   }else if(value.startsWith("https://")){
    BrandingSettings existing=dao.get(tenantId);
    if(existing!=null&&value.equals("LOGO".equals(slot)?existing.logoUrl():existing.bannerUrl()))return value;
   }
  }catch(IllegalArgumentException ignored){}
  throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Choose a cropped photo. New external image links are not supported.");
 }
 private byte[] decode(MultipartFile file,ImageOptimizer.Kind kind){
  var image=ImageOptimizer.optimize(file,kind);return image==null?null:image.bytes();
 }
}
