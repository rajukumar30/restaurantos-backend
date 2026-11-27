package com.restaurantos.modules.branding.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurantos.modules.branding.dto.BrandingSettings;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

@Repository
public class BrandingDao {
 public static final String COLUMNS="COALESCE(gb.slogan,'') AS slogan, COALESCE(gb.logo_url,'') AS logo_url, COALESCE(gb.banner_url,'') AS banner_url, COALESCE(gb.theme,'CLASSIC') AS theme, gb.settings_json::text AS settings_json";
 private final JdbcTemplate jdbc;
 private final ObjectMapper mapper;
 public BrandingDao(JdbcTemplate jdbc,ObjectMapper mapper){this.jdbc=jdbc;this.mapper=mapper;}
 public static BrandingSettings read(ResultSet rs,ObjectMapper mapper,String name)throws SQLException{
  String json=rs.getString("settings_json");
  if(json!=null)try{return mapper.readValue(json,BrandingSettings.class);}catch(JsonProcessingException e){throw new IllegalStateException("Invalid stored branding configuration",e);}
  return BrandingSettings.legacy(name,rs.getString("slogan"),rs.getString("logo_url"),rs.getString("banner_url"),rs.getString("theme"));
 }
 public BrandingSettings get(UUID tenantId){
  return jdbc.queryForObject("SELECT t.name, "+COLUMNS+" FROM tenants t LEFT JOIN restaurant_guest_branding gb ON gb.tenant_id=t.id WHERE t.id=? AND t.status='ACTIVE'",(rs,row)->read(rs,mapper,rs.getString("name")),tenantId);
 }
 public boolean canManage(UUID tenantId,UUID userId){
  return Boolean.TRUE.equals(jdbc.queryForObject("""
   SELECT EXISTS (SELECT 1 FROM tenant_users tu
    JOIN tenants t ON t.id=tu.tenant_id AND t.status='ACTIVE'
    JOIN user_roles ur ON ur.tenant_id=tu.tenant_id AND ur.tenant_user_id=tu.id
    JOIN roles r ON r.id=ur.role_id AND r.tenant_id=tu.tenant_id
    JOIN role_permissions rp ON rp.role_id=r.id
    JOIN permissions p ON p.id=rp.permission_id
    WHERE tu.tenant_id=? AND tu.user_id=? AND tu.status='ACTIVE'
      AND r.role_key='OWNER' AND ur.branch_id IS NULL
      AND p.permission_key='menu:manage')
   """,Boolean.class,tenantId,userId));
 }
 public void lockTenant(UUID tenantId){
  jdbc.queryForObject("SELECT id FROM tenants WHERE id=? AND status='ACTIVE' FOR UPDATE",UUID.class,tenantId);
 }
 public String storeImage(UUID tenantId,String slot,byte[] bytes){
  UUID id=UUID.randomUUID();
  jdbc.update("""
   INSERT INTO restaurant_brand_images(id,tenant_id,slot,image_bytes) VALUES(?,?,?,?)
   ON CONFLICT(tenant_id,slot) DO UPDATE SET id=EXCLUDED.id,image_bytes=EXCLUDED.image_bytes
   """,id,tenantId,slot,bytes);
  return "/api/v1/public/guest-website/images/"+id;
 }
 public boolean ownsImage(UUID tenantId,String slot,UUID id){
  return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM restaurant_brand_images WHERE tenant_id=? AND slot=? AND id=?)",Boolean.class,tenantId,slot,id));
 }
 public void removeImage(UUID tenantId,String slot){
  jdbc.update("DELETE FROM restaurant_brand_images WHERE tenant_id=? AND slot=?",tenantId,slot);
 }
 public java.util.Optional<byte[]> publishedImage(UUID id){
  return jdbc.query("""
   SELECT i.image_bytes FROM restaurant_brand_images i
   JOIN tenants t ON t.id=i.tenant_id AND t.status='ACTIVE'
   JOIN restaurant_guest_branding gb ON gb.tenant_id=i.tenant_id
   WHERE i.id=? AND (CASE WHEN i.slot='LOGO' THEN gb.logo_url ELSE gb.banner_url END)
     = '/api/v1/public/guest-website/images/' || CAST(i.id AS text)
   """,(rs,row)->rs.getBytes(1),id).stream().findFirst();
 }
 public void publish(UUID tenantId,BrandingSettings settings){
  String json;
  try{json=mapper.writeValueAsString(settings);}catch(JsonProcessingException e){throw new IllegalStateException("Cannot serialize branding",e);}
  jdbc.update("""
   INSERT INTO restaurant_guest_branding(tenant_id,slogan,logo_url,banner_url,theme,settings_json)
   VALUES(?,?,?,?,?,CAST(? AS jsonb))
   ON CONFLICT(tenant_id) DO UPDATE SET slogan=EXCLUDED.slogan,logo_url=EXCLUDED.logo_url,
      banner_url=EXCLUDED.banner_url,theme=EXCLUDED.theme,settings_json=EXCLUDED.settings_json,updated_at=NOW()
   """,tenantId,settings.slogan(),settings.logoUrl(),settings.bannerUrl(),settings.theme(),json);
 }
}
