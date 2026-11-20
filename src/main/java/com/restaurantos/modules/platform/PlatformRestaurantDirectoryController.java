package com.restaurantos.modules.platform;

import com.restaurantos.common.api.ApiResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/platform/restaurants")
public class PlatformRestaurantDirectoryController {
    private final JdbcTemplate jdbc;
    public PlatformRestaurantDirectoryController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public record Restaurant(UUID id, String name, String slug, String subdomain,
                             String status, OffsetDateTime createdAt) {}

    @GetMapping
    public ApiResponse<List<Restaurant>> list(@RequestParam(defaultValue = "") String search,
                                              @RequestParam(defaultValue = "0") int page) {
        String term = search.strip();
        if (term.length() > 100) throw new org.springframework.web.server.ResponseStatusException(
            org.springframework.http.HttpStatus.BAD_REQUEST, "Search is too long");
        if (page < 0 || page > 100000) throw new org.springframework.web.server.ResponseStatusException(
            org.springframework.http.HttpStatus.BAD_REQUEST, "Invalid page");
        List<Restaurant> rows = jdbc.query("""
            SELECT id, name, slug, subdomain, status, created_at
            FROM tenants
            WHERE (? = '' OR name ILIKE '%' || ? || '%' OR slug ILIKE '%' || ? || '%')
            ORDER BY created_at DESC, id DESC
            LIMIT 25 OFFSET ?
            """, (rs, i) -> new Restaurant(
                rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("slug"),
                rs.getString("subdomain"), rs.getString("status"),
                rs.getObject("created_at", OffsetDateTime.class)),
            term, term, term, page * 25);
        return ApiResponse.success("Restaurants", rows);
    }

    @GetMapping("/{tenantId}")
    public ApiResponse<Restaurant> details(@PathVariable UUID tenantId) {
        return ApiResponse.success("Restaurant details", find(tenantId));
    }

    private Restaurant find(UUID id) {
        return jdbc.query("""
            SELECT id,name,slug,subdomain,status,created_at FROM tenants WHERE id=?
            """, rs -> {
                if (!rs.next()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Restaurant not found");
                return new Restaurant(rs.getObject("id",UUID.class),rs.getString("name"),
                    rs.getString("slug"),rs.getString("subdomain"),rs.getString("status"),
                    rs.getObject("created_at",OffsetDateTime.class));
            }, id);
    }

    public record BranchDetail(UUID id, String name, String branchCode, String phone,
                               String status, String timezone, String address) {}
    public record OwnerDetail(UUID id, String name, String email, String phone, String status) {}
    public record RestaurantProfile(Restaurant restaurant, List<BranchDetail> branches,
                                    List<OwnerDetail> owners) {}

    @GetMapping("/{tenantId}/profile")
    public ApiResponse<RestaurantProfile> profile(@PathVariable UUID tenantId) {
        Restaurant restaurant=find(tenantId);
        List<BranchDetail> branches=jdbc.query("""
            SELECT id,name,branch_code,phone,status,timezone,address::text AS address_text
            FROM branches WHERE tenant_id=? ORDER BY created_at,id
            """,(rs,i)->new BranchDetail(rs.getObject("id",UUID.class),rs.getString("name"),
                rs.getString("branch_code"),rs.getString("phone"),rs.getString("status"),
                rs.getString("timezone"),rs.getString("address_text")),tenantId);
        List<OwnerDetail> owners=jdbc.query("""
            SELECT DISTINCT u.id,u.name,u.email,u.phone,u.status
            FROM app_users u
            JOIN tenant_users tu ON tu.user_id=u.id AND tu.tenant_id=?
            JOIN user_roles ur ON ur.tenant_user_id=tu.id AND ur.tenant_id=tu.tenant_id
            JOIN roles r ON r.id=ur.role_id
            WHERE UPPER(r.role_key) IN ('OWNER','RESTAURANT_OWNER')
            ORDER BY u.name,u.id
            """,(rs,i)->new OwnerDetail(rs.getObject("id",UUID.class),rs.getString("name"),
                rs.getString("email"),rs.getString("phone"),rs.getString("status")),tenantId);
        return ApiResponse.success("Restaurant profile",new RestaurantProfile(restaurant,branches,owners));
    }

    public record UpdateRestaurant(String name, String status) {}

    @PatchMapping("/{tenantId}")
    @Transactional
    public ApiResponse<Restaurant> update(@PathVariable UUID tenantId,
                                         @RequestBody UpdateRestaurant request,
                                         Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UUID actorId))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Authentication required");
        Restaurant before = find(tenantId);
        String name = request.name() == null ? before.name() : request.name().strip();
        String status = request.status() == null ? before.status() : request.status();
        if (name.isBlank() || name.length() > 150)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Restaurant name must be 1–150 characters");
        if (!status.equals("ACTIVE") && !status.equals("INACTIVE"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Status must be ACTIVE or INACTIVE");
        jdbc.update("UPDATE tenants SET name=?,status=?,updated_at=NOW() WHERE id=?",name,status,tenantId);
        jdbc.update("""
            INSERT INTO platform_admin_audit_logs(actor_user_id,tenant_id,action)
            VALUES(?,?,'RESTAURANT_PROFILE_UPDATED')
            """,actorId,tenantId);
        return ApiResponse.success("Restaurant updated",find(tenantId));
    }

    public record UpdateBranch(String name, String phone, String status) {}

    @PatchMapping("/{tenantId}/branches/{branchId}")
    @Transactional
    public ApiResponse<BranchDetail> updateBranch(@PathVariable UUID tenantId,
                                                   @PathVariable UUID branchId,
                                                   @RequestBody UpdateBranch request,
                                                   Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UUID actorId))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        find(tenantId);
        BranchDetail before = jdbc.query("""
            SELECT id,name,branch_code,phone,status,timezone,address::text AS address_text
            FROM branches WHERE tenant_id=? AND id=? FOR UPDATE
            """, rs -> {
                if (!rs.next()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Branch not found");
                return new BranchDetail(rs.getObject("id",UUID.class),rs.getString("name"),
                    rs.getString("branch_code"),rs.getString("phone"),rs.getString("status"),
                    rs.getString("timezone"),rs.getString("address_text"));
            }, tenantId,branchId);
        String name = request.name() == null ? before.name() : request.name().strip();
        String phone = request.phone() == null ? before.phone() : request.phone().strip();
        String status = request.status() == null ? before.status() : request.status();
        if (name.isBlank() || name.length() > 150)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Branch name must be 1–150 characters");
        if (phone != null && phone.length() > 30)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Phone must be at most 30 characters");
        if (!"ACTIVE".equals(status) && !"INACTIVE".equals(status))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid branch status");
        jdbc.update("""
            UPDATE branches SET name=?,phone=?,status=?,updated_at=NOW()
            WHERE tenant_id=? AND id=?
            """,name,phone,status,tenantId,branchId);
        jdbc.update("""
            INSERT INTO platform_admin_audit_logs(actor_user_id,tenant_id,action)
            VALUES(?,?,'BRANCH_PROFILE_UPDATED')
            """,actorId,tenantId);
        return ApiResponse.success("Branch updated",
            new BranchDetail(before.id(),name,before.branchCode(),phone,status,before.timezone(),before.address()));
    }
}
