package com.restaurantos.modules.auth.repository;
import com.restaurantos.modules.auth.entity.AuthUser;
import com.restaurantos.modules.auth.dto.TenantMembership;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;
@Repository
public class AuthDao {
 private final JdbcTemplate jdbc;
 public AuthDao(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public List<AuthUser> findByEmail(String email){
  return jdbc.query("SELECT id,name,email,password_hash,status FROM app_users WHERE lower(email)=lower(?)",
   (rs,i)->new AuthUser(rs.getObject("id",UUID.class),rs.getString("name"),rs.getString("email"),rs.getString("password_hash"),rs.getString("status")),email);
 }
 public boolean isPlatformAdmin(UUID userId){
  Integer count=jdbc.queryForObject("SELECT COUNT(*) FROM platform_admins WHERE user_id=? AND status='ACTIVE'",Integer.class,userId);
  return count!=null&&count>0;
 }
 public List<TenantMembership> activeMemberships(UUID userId){
  return jdbc.query("""
   SELECT tu.tenant_id,t.name AS tenant_name,
          string_agg(DISTINCT r.display_name,', ' ORDER BY r.display_name) AS role_names
   FROM tenant_users tu
   JOIN tenants t ON t.id=tu.tenant_id AND t.status='ACTIVE'
   LEFT JOIN user_roles ur ON ur.tenant_id=tu.tenant_id AND ur.tenant_user_id=tu.id
   LEFT JOIN roles r ON r.id=ur.role_id
   WHERE tu.user_id=? AND tu.status='ACTIVE'
   GROUP BY tu.tenant_id,t.name ORDER BY t.name
   """,(rs,i)->new TenantMembership(rs.getObject("tenant_id",UUID.class),rs.getString("tenant_name"),rs.getString("role_names")),userId);
 }
}
