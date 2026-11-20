package com.restaurantos.modules.platform;

import com.restaurantos.common.api.ApiResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/platform/team")
public class PlatformTeamController {
    private final JdbcTemplate jdbc;
    public PlatformTeamController(JdbcTemplate jdbc) { this.jdbc=jdbc; }

    public record TeamMember(UUID id, String name, String email, String phone,
                             String accountStatus, String adminStatus,
                             String roles, OffsetDateTime addedAt) {}

    @GetMapping
    public ApiResponse<List<TeamMember>> list() {
        List<TeamMember> members=jdbc.query("""
            SELECT pa.id,u.name,u.email,u.phone,u.status AS account_status,
                   pa.status AS admin_status,pa.created_at,
                   COALESCE(STRING_AGG(DISTINCT r.display_name,', ' ORDER BY r.display_name),'No role assigned') AS roles
            FROM platform_admins pa
            JOIN app_users u ON u.id=pa.user_id
            LEFT JOIN platform_admin_roles par ON par.platform_admin_id=pa.id
            LEFT JOIN roles r ON r.id=par.role_id
            GROUP BY pa.id,u.id,u.name,u.email,u.phone,u.status,pa.status,pa.created_at
            ORDER BY pa.created_at DESC,pa.id DESC
            """,(rs,i)->new TeamMember(rs.getObject("id",UUID.class),rs.getString("name"),
                rs.getString("email"),rs.getString("phone"),rs.getString("account_status"),
                rs.getString("admin_status"),rs.getString("roles"),
                rs.getObject("created_at",OffsetDateTime.class)));
        return ApiResponse.success("Platform administrators",members);
    }
}
