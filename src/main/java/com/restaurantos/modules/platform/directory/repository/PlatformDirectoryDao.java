package com.restaurantos.modules.platform.directory.repository;
import com.restaurantos.modules.platform.directory.dto.TeamMemberResponse;
import com.restaurantos.modules.platform.directory.dto.MembershipResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
@Repository
public class PlatformDirectoryDao {
 private final JdbcTemplate jdbc;
 public PlatformDirectoryDao(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public List<TeamMemberResponse> team(){
  return jdbc.query("""
   SELECT pa.id,u.name,u.email,u.phone,u.status AS account_status,
          pa.status AS admin_status,pa.created_at,
          COALESCE(STRING_AGG(DISTINCT r.display_name,', ' ORDER BY r.display_name),'No role assigned') AS roles
   FROM platform_admins pa JOIN app_users u ON u.id=pa.user_id
   LEFT JOIN platform_admin_roles par ON par.platform_admin_id=pa.id
   LEFT JOIN roles r ON r.id=par.role_id
   GROUP BY pa.id,u.id,u.name,u.email,u.phone,u.status,pa.status,pa.created_at
   ORDER BY pa.created_at DESC,pa.id DESC
   """,(rs,i)->new TeamMemberResponse(rs.getObject("id",UUID.class),rs.getString("name"),
    rs.getString("email"),rs.getString("phone"),rs.getString("account_status"),
    rs.getString("admin_status"),rs.getString("roles"),rs.getObject("created_at",OffsetDateTime.class)));
 }
 public List<MembershipResponse> memberships(String term,UUID tenantId,int page){
  return jdbc.query("""
   SELECT tu.id AS membership_id,u.id AS user_id,u.name,u.email,u.phone,
          u.status AS account_status,tu.status AS membership_status,
          t.id AS tenant_id,t.name AS restaurant_name,tu.created_at,
          COALESCE(STRING_AGG(DISTINCT r.display_name,', ' ORDER BY r.display_name),'No role') AS roles
   FROM tenant_users tu JOIN app_users u ON u.id=tu.user_id
   JOIN tenants t ON t.id=tu.tenant_id
   LEFT JOIN user_roles ur ON ur.tenant_id=tu.tenant_id AND ur.tenant_user_id=tu.id
   LEFT JOIN roles r ON r.id=ur.role_id AND r.tenant_id=tu.tenant_id
   WHERE (CAST(? AS UUID) IS NULL OR tu.tenant_id=CAST(? AS UUID))
     AND (?='' OR u.name ILIKE '%'||?||'%' OR u.email ILIKE '%'||?||'%')
   GROUP BY tu.id,u.id,u.name,u.email,u.phone,u.status,tu.status,t.id,t.name,tu.created_at
   ORDER BY tu.created_at DESC,tu.id DESC LIMIT 25 OFFSET ?
   """,(rs,i)->new MembershipResponse(rs.getObject("membership_id",UUID.class),
    rs.getObject("user_id",UUID.class),rs.getString("name"),rs.getString("email"),
    rs.getString("phone"),rs.getString("account_status"),rs.getString("membership_status"),
    rs.getObject("tenant_id",UUID.class),rs.getString("restaurant_name"),
    rs.getString("roles"),rs.getObject("created_at",OffsetDateTime.class)),
    tenantId,tenantId,term,term,term,page*25);
 }
}
