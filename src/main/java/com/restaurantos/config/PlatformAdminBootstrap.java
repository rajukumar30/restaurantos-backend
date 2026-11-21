package com.restaurantos.config;

import com.restaurantos.config.repository.PlatformAdminBootstrapDao;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.Locale;
import java.util.UUID;

@Component
public class PlatformAdminBootstrap implements ApplicationRunner {
    private final PlatformAdminBootstrapDao dao;
    private final PasswordEncoder encoder;
    private final String email;
    private final String password;
    private final String name;

    public PlatformAdminBootstrap(PlatformAdminBootstrapDao dao, PasswordEncoder encoder,
        @Value("${restaurantos.bootstrap.admin-email:}") String email,
        @Value("${restaurantos.bootstrap.admin-password:}") String password,
        @Value("${restaurantos.bootstrap.admin-name:Platform Administrator}") String name) {
        this.dao=dao; this.encoder=encoder; this.email=email; this.password=password; this.name=name;
    }
    @Override @Transactional
    public void run(ApplicationArguments args) {
        if (email.isBlank() && password.isBlank()) return;
        if (email.isBlank() || password.length() < 12)
            throw new IllegalStateException("Set PLATFORM_ADMIN_EMAIL and a PLATFORM_ADMIN_PASSWORD of at least 12 characters.");
        if (dao.adminExists(email)) return;
        UUID userId=dao.existingUser(email);
        if (userId == null) {
            userId=UUID.randomUUID();
            dao.createUser(userId,name,email.toLowerCase(Locale.ROOT),encoder.encode(password));
        }
        dao.grantPlatformAdmin(userId);
    }
}
