package com.restaurantos.modules.auth;

import com.restaurantos.common.api.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthController(JdbcTemplate jdbc, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        var users = jdbc.query("""
                SELECT id, name, email, password_hash, status
                FROM app_users WHERE lower(email) = lower(?)
                """, (rs, row) -> new UserRow(rs.getObject("id", UUID.class), rs.getString("name"),
                rs.getString("email"), rs.getString("password_hash"), rs.getString("status")), request.email());
        if (users.isEmpty() || !"ACTIVE".equals(users.getFirst().status())
                || !passwordEncoder.matches(request.password(), users.getFirst().passwordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }
        UserRow user = users.getFirst();
        List<String> authorities = new ArrayList<>();
        Integer adminCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM platform_admins WHERE user_id = ? AND status = 'ACTIVE'",
                Integer.class, user.id());
        if (adminCount != null && adminCount > 0) authorities.add("PLATFORM_ADMIN");

        List<TenantMembership> memberships = jdbc.query("""
                SELECT tu.tenant_id, t.name AS tenant_name,
                       string_agg(DISTINCT r.display_name, ', ' ORDER BY r.display_name) AS role_names
                FROM tenant_users tu
                JOIN tenants t ON t.id = tu.tenant_id AND t.status = 'ACTIVE'
                LEFT JOIN user_roles ur ON ur.tenant_id = tu.tenant_id AND ur.tenant_user_id = tu.id
                LEFT JOIN roles r ON r.id = ur.role_id
                WHERE tu.user_id = ? AND tu.status = 'ACTIVE'
                GROUP BY tu.tenant_id, t.name
                ORDER BY t.name
                """, (rs, row) -> new TenantMembership(rs.getObject("tenant_id", UUID.class),
                rs.getString("tenant_name"), rs.getString("role_names")), user.id());
        if (!memberships.isEmpty()) authorities.add("TENANT_USER");
        if (authorities.isEmpty()) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No active platform access");

        String token = jwtService.issue(user.id(), user.email(), authorities);
        return ApiResponse.success("Login successful",
                new LoginResponse(token, "Bearer", user.id(), user.name(), user.email(), authorities, memberships));
    }

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {}
    public record TenantMembership(UUID tenantId, String tenantName, String roles) {}
    public record LoginResponse(String accessToken, String tokenType, UUID userId, String name,
                                String email, List<String> authorities, List<TenantMembership> memberships) {}
    private record UserRow(UUID id, String name, String email, String passwordHash, String status) {}
}