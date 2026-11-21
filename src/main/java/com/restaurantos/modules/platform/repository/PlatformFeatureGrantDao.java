package com.restaurantos.modules.platform.repository;
import com.restaurantos.modules.platform.dto.PlatformFeatureGrantDto.Grant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.time.OffsetDateTime;
import java.util.*;
@Repository
public class PlatformFeatureGrantDao {
 private final JdbcTemplate jdbc;
 public PlatformFeatureGrantDao(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public boolean activeTenant(UUID id){return Boolean.TRUE.equals(jdbc.queryForObject(
  "SELECT EXISTS(SELECT 1 FROM tenants WHERE id=? AND status='ACTIVE')",Boolean.class,id));}
 public boolean availableFeature(String key){return Boolean.TRUE.equals(jdbc.queryForObject(
  "SELECT EXISTS(SELECT 1 FROM feature_catalog WHERE feature_key=? AND available=TRUE)",Boolean.class,key));}
 public void lockTenant(UUID id){jdbc.queryForObject("SELECT id FROM tenants WHERE id=? FOR UPDATE",UUID.class,id);}
 private Grant map(java.sql.ResultSet rs,int i)throws java.sql.SQLException {
  return new Grant(rs.getObject("id",UUID.class),rs.getObject("tenant_id",UUID.class),
   rs.getString("feature_key"),rs.getObject("starts_at",OffsetDateTime.class),
   rs.getObject("expires_at",OffsetDateTime.class),rs.getObject("revoked_at",OffsetDateTime.class),
   rs.getObject("granted_by",UUID.class));
 }
 public List<Grant> list(UUID tenant){return jdbc.query(
  "SELECT * FROM tenant_feature_grants WHERE tenant_id=? ORDER BY created_at DESC LIMIT 200",
  this::map,tenant);}
 public void insert(UUID tenant,String feature,OffsetDateTime expiry,UUID actor){
  jdbc.update("INSERT INTO tenant_feature_grants(tenant_id,feature_key,expires_at,granted_by) VALUES(?,?,?,?)",
   tenant,feature,expiry,actor);
 }
 public boolean revoke(UUID tenant,UUID grant){
  return jdbc.update("UPDATE tenant_feature_grants SET revoked_at=NOW() WHERE id=? AND tenant_id=? AND revoked_at IS NULL",
   grant,tenant)>0;
 }
 public void audit(UUID actor,UUID tenant,String action,String feature){
  jdbc.update("INSERT INTO platform_admin_audit_logs(actor_user_id,tenant_id,action,feature_key) VALUES(?,?,?,?)",
   actor,tenant,action,feature);
 }
 public String featureOf(UUID tenant,UUID grant){
  return jdbc.query("SELECT feature_key FROM tenant_feature_grants WHERE tenant_id=? AND id=?",
   (rs,i)->rs.getString(1),tenant,grant).stream().findFirst().orElse(null);
 }
}
