package com.restaurantos.modules.platform;

import com.restaurantos.common.api.ApiResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import java.time.OffsetDateTime;
import java.util.List;
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
}
