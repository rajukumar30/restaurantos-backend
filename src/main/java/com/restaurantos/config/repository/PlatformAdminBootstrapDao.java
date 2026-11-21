package com.restaurantos.config.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.*;

@Repository
public class PlatformAdminBootstrapDao {
    private final JdbcTemplate jdbc;
    public PlatformAdminBootstrapDao(JdbcTemplate jdbc) { this.jdbc=jdbc; }

    public boolean adminExists(String email) {
        Integer count = jdbc.queryForObject("""
            SELECT COUNT(*) FROM platform_admins pa
            JOIN app_users u ON u.id=pa.user_id WHERE lower(u.email)=lower(?)
            """, Integer.class, email);
        return count != null && count > 0;
    }
    public UUID existingUser(String email) {
        return jdbc.query("SELECT id FROM app_users WHERE lower(email)=lower(?)",
            (rs,i) -> rs.getObject("id",UUID.class), email).stream().findFirst().orElse(null);
    }
    public void createUser(UUID id, String name, String email, String hash) {
        jdbc.update("INSERT INTO app_users (id,name,email,password_hash,status) VALUES (?,?,?,?, 'ACTIVE')",
            id, name, email, hash);
    }
    public void grantPlatformAdmin(UUID userId) {
        jdbc.update("INSERT INTO platform_admins (user_id,status) VALUES (?, 'ACTIVE')", userId);
    }
}
