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
import java.util.Map;
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
                """, (rs, row) -> new UserRow(UUID.fromString(rs.getString("id")), rs.getString("name"),
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
        Integer tenantCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tenant_users WHERE user_id = ? AND status = 'ACTIVE'",
                Integer.class, user.id());
        if (tenantCount != null && tenantCount > 0) authorities.add("TENANT_USER");
        if (authorities.isEmpty()) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No active platform access");
        String token = jwtService.issue(user.id(), user.email(), authorities);
        return ApiResponse.success("Login successful",
                new LoginResponse(token, "Bearer", user.id(), user.name(), user.email(), authorities));
    }

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {}
    public record LoginResponse(String accessToken, String tokenType, UUID userId, String name,
                                String email, List<String> authorities) {}
    private record UserRow(UUID id, String name, String email, String passwordHash, String status) {}
}