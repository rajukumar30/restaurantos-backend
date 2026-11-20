package com.restaurantos.modules.platform;

import com.restaurantos.common.api.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/platform/users")
public class PlatformUsersController {
    private final JdbcTemplate jdbc;
    public PlatformUsersController(JdbcTemplate jdbc) { this.jdbc=jdbc; }

    public record Membership(UUID membershipId, UUID userId, String name, String email,
                             String phone, String accountStatus, String membershipStatus,
                             UUID tenantId, String restaurantName, String roles,
                             OffsetDateTime joinedAt) {}

    @GetMapping
    public ApiResponse<List<Membership>> list(@RequestParam(defaultValue="") String search,
                                             @RequestParam(required=false) UUID tenantId,
                                             @RequestParam(defaultValue="0") int page) {
        String term=search.strip();
        if(term.length()>100 || page<0 || page>100000)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid search or page");
        List<Membership> users=jdbc.query("""
            SELECT tu.id AS membership_id,u.id AS user_id,u.name,u.email,u.phone,
                   u.status AS account_status,tu.status AS membership_status,
                   t.id AS tenant_id,t.name AS restaurant_name,tu.created_at,
                   COALESCE(STRING_AGG(DISTINCT r.display_name,', ' ORDER BY r.display_name),'No role') AS roles
            FROM tenant_users tu
            JOIN app_users u ON u.id=tu.user_id
            JOIN tenants t ON t.id=tu.tenant_id
            LEFT JOIN user_roles ur ON ur.tenant_id=tu.tenant_id AND ur.tenant_user_id=tu.id
            LEFT JOIN roles r ON r.id=ur.role_id AND r.tenant_id=tu.tenant_id
            WHERE (CAST(? AS UUID) IS NULL OR tu.tenant_id=CAST(? AS UUID))
              AND (?='' OR u.name ILIKE '%'||?||'%' OR u.email ILIKE '%'||?||'%')
            GROUP BY tu.id,u.id,u.name,u.email,u.phone,u.status,tu.status,
                     t.id,t.name,tu.created_at
            ORDER BY tu.created_at DESC,tu.id DESC
            LIMIT 25 OFFSET ?
            """,(rs,i)->new Membership(rs.getObject("membership_id",UUID.class),
                rs.getObject("user_id",UUID.class),rs.getString("name"),
                rs.getString("email"),rs.getString("phone"),
                rs.getString("account_status"),rs.getString("membership_status"),
                rs.getObject("tenant_id",UUID.class),rs.getString("restaurant_name"),
                rs.getString("roles"),rs.getObject("created_at",OffsetDateTime.class)),
            tenantId,tenantId,term,term,term,page*25);
        return ApiResponse.success("Restaurant user memberships",users);
    }
}
