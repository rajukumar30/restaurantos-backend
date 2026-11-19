package com.restaurantos.modules.platform;

import com.restaurantos.common.api.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/platform/restaurants")
public class RestaurantOnboardingController {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private static final Set<String> RESERVED_SUBDOMAINS = Set.of("www", "admin", "api", "app", "support", "login", "mail", "status", "docs");

    public RestaurantOnboardingController(JdbcTemplate jdbc, PasswordEncoder encoder) {
        this.jdbc = jdbc;
        this.encoder = encoder;
    }

    @PostMapping
    @Transactional
    public ApiResponse<RestaurantCreated> create(@Valid @RequestBody CreateRestaurantRequest request) {
        String slug = slugify(request.restaurantName());
        Integer slugCount = jdbc.queryForObject("SELECT COUNT(*) FROM tenants WHERE slug = ?", Integer.class, slug);
        if (slugCount != null && slugCount > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A restaurant with this name already exists; choose a unique name.");
        }
        String subdomain = createAvailableSubdomain(slug);
        Integer emailCount = jdbc.queryForObject("SELECT COUNT(*) FROM app_users WHERE lower(email)=lower(?)", Integer.class, request.ownerEmail());
        if (emailCount != null && emailCount > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this owner email already exists.");
        }

        UUID tenantId = UUID.randomUUID();
        UUID branchId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID tenantUserId = UUID.randomUUID();
        UUID branchUserId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();

        jdbc.update("INSERT INTO tenants (id,name,slug,subdomain) VALUES (?,?,?,?)", tenantId, request.restaurantName().trim(), slug, subdomain);
        jdbc.update("INSERT INTO branches (id,tenant_id,name,branch_code,address,phone) VALUES (?,?,?,'MAIN',CAST(? AS jsonb),?)",
                branchId, tenantId, request.branchName().trim(),
                request.addressJson() == null || request.addressJson().isBlank() ? "{}" : request.addressJson(),
                request.branchPhone());
        jdbc.update("INSERT INTO app_users (id,name,email,phone,password_hash,status) VALUES (?,?,?,?,?,'ACTIVE')",
                userId, request.ownerName().trim(), request.ownerEmail().toLowerCase(Locale.ROOT),
                request.ownerPhone(), encoder.encode(request.ownerPassword()));
        jdbc.update("INSERT INTO tenant_users (id,tenant_id,user_id,status) VALUES (?,?,?,'ACTIVE')",
                tenantUserId, tenantId, userId);
        jdbc.update("INSERT INTO branch_users (id,tenant_id,branch_id,tenant_user_id,status) VALUES (?,?,?,?,'ACTIVE')",
                branchUserId, tenantId, branchId, tenantUserId);
        jdbc.update("INSERT INTO roles (id,tenant_id,role_key,display_name,is_system) VALUES (?,?,'OWNER','Restaurant Owner',true)",
                roleId, tenantId);
        jdbc.update("""
                INSERT INTO role_permissions (role_id, permission_id)
                SELECT ?, id FROM permissions
                ON CONFLICT DO NOTHING
                """, roleId);
        jdbc.update("INSERT INTO user_roles (tenant_id,tenant_user_id,role_id,branch_id) VALUES (?,?,?,NULL)",
                tenantId, tenantUserId, roleId);

        return ApiResponse.success("Restaurant and owner account created",
                new RestaurantCreated(tenantId, branchId, userId, slug, subdomain, "/r/" + slug + "/menu", "ACTIVE"));
    }

    private String createAvailableSubdomain(String slug) {
        String candidate = slug;
        if (candidate.length() > 63) {
            candidate = candidate.substring(0, 54).replaceAll("-+$", "") + "-"
                    + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        }
        if (RESERVED_SUBDOMAINS.contains(candidate)) candidate = "restaurant-" + candidate;
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM tenants WHERE subdomain = ?", Integer.class, candidate);
        if (count != null && count > 0) {
            candidate = candidate.substring(0, Math.min(candidate.length(), 54)).replaceAll("-+$", "") + "-"
                    + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        }
        return candidate;
    }

    private String slugify(String value) {
        String slug = value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        if (slug.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Restaurant name must contain letters or numbers.");
        return slug;
    }

    public record CreateRestaurantRequest(
            @NotBlank @Size(max=150) String restaurantName,
            @NotBlank @Size(max=150) String branchName,
            @NotBlank @Size(max=150) String ownerName,
            @NotBlank @Email @Size(max=255) String ownerEmail,
            @NotBlank @Size(min=12,max=100) String ownerPassword,
            @Size(max=20) String ownerPhone,
            @Size(max=20) String branchPhone,
            String addressJson) {}
    public record RestaurantCreated(UUID tenantId, UUID branchId, UUID ownerUserId, String slug, String subdomain, String restaurantUrl, String status) {}
}