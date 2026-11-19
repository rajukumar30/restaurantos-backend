package com.restaurantos.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class PlatformAdminBootstrap implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final String email;
    private final String password;
    private final String name;

    public PlatformAdminBootstrap(JdbcTemplate jdbc, PasswordEncoder encoder,
            @Value("${restaurantos.bootstrap.admin-email:}") String email,
            @Value("${restaurantos.bootstrap.admin-password:}") String password,
            @Value("${restaurantos.bootstrap.admin-name:Platform Administrator}") String name) {
        this.jdbc = jdbc;
        this.encoder = encoder;
        this.email = email;
        this.password = password;
        this.name = name;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (email.isBlank() && password.isBlank()) return;
        if (email.isBlank() || password.length() < 12) {
            throw new IllegalStateException("Set PLATFORM_ADMIN_EMAIL and a PLATFORM_ADMIN_PASSWORD of at least 12 characters.");
        }
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM platform_admins pa JOIN app_users u ON u.id=pa.user_id WHERE lower(u.email)=lower(?)", Integer.class, email);
        if (count != null && count > 0) return;
        var existing = jdbc.query("SELECT id FROM app_users WHERE lower(email)=lower(?)",
                (rs, row) -> rs.getObject("id", java.util.UUID.class), email);
        java.util.UUID userId;
        if (existing.isEmpty()) {
            userId = java.util.UUID.randomUUID();
            jdbc.update("INSERT INTO app_users (id,name,email,password_hash,status) VALUES (?,?,?,?, 'ACTIVE')",
                    userId, name, email.toLowerCase(), encoder.encode(password));
        } else {
            userId = existing.getFirst();
        }
        jdbc.update("INSERT INTO platform_admins (user_id,status) VALUES (?, 'ACTIVE')", userId);
    }
}