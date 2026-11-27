package com.restaurantos.modules.auth.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public class PlatformAdminAccessDao {
    private final JdbcTemplate jdbc;
    public PlatformAdminAccessDao(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public boolean active(UUID userId) {
        Integer count = jdbc.queryForObject("""
            SELECT COUNT(*) FROM platform_admins pa
            JOIN app_users u ON u.id=pa.user_id
            WHERE pa.user_id=? AND pa.status='ACTIVE' AND u.status='ACTIVE'
            """, Integer.class, userId);
        return count != null && count > 0;
    }
}
