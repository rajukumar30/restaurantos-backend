package com.restaurantos.modules.platform.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

/** Public, read-only lookup for an active restaurant's provisioned subdomain. */
@Repository
public class PublicRestaurantDomainDao {
    private final JdbcTemplate jdbc;
    public PublicRestaurantDomainDao(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Optional<String> findActiveSlugBySubdomain(String subdomain) {
        List<String> slugs = jdbc.queryForList(
            "SELECT slug FROM tenants WHERE lower(subdomain) = ? AND status = 'ACTIVE' LIMIT 1",
            String.class, subdomain);
        return slugs.stream().findFirst();
    }
}
