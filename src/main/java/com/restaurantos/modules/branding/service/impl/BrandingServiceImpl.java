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
import java.util.Optional;
import org.springframework.web.multipart.MultipartFile;
import javax.imageio.ImageIO;
import java.io.*;
import java.awt.image.BufferedImage;
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
   throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Active owner or manager access with menu management permission is required to customize the guest website.");
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
  authorize(tenantId,auth);return save(tenantId,settings,decode(logo),decode(banner));
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
   }else{
    URI uri=URI.create(value);
    if("https".equalsIgnoreCase(uri.getScheme())&&uri.getHost()!=null&&uri.getUserInfo()==null&&uri.getFragment()==null)return value;
   }
  }catch(IllegalArgumentException ignored){}
  throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Use an uploaded image or a valid HTTPS image URL.");
 }
 private byte[] decode(MultipartFile file){
  if(file==null)return null;
  if(file.isEmpty()||file.getSize()>512000)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Compressed image must be smaller than 500 KB.");
  try(var input=ImageIO.createImageInputStream(new ByteArrayInputStream(file.getBytes()))){
   var readers=ImageIO.getImageReaders(input);
   if(!readers.hasNext())throw new IOException("Unrecognized image");
   var reader=readers.next();
   try{
    reader.setInput(input,true,true);
    if(!"JPEG".equalsIgnoreCase(reader.getFormatName())||reader.getWidth(0)>1600||reader.getHeight(0)>1600)throw new IOException("Unsupported image size or format");
    BufferedImage image=reader.read(0);
    var out=new ByteArrayOutputStream();
    if(!ImageIO.write(image,"JPEG",out)||out.size()>512000)throw new IOException("Image too large");
    return out.toByteArray();
   }finally{reader.dispose();}
  }catch(IOException|RuntimeException e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Choose a valid photo and try again.");}
 }
}
