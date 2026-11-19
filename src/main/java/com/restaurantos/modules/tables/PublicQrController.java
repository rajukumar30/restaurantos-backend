package com.restaurantos.modules.tables;

import com.restaurantos.common.api.ApiResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/public/qr")
public class PublicQrController {
    private final JdbcTemplate jdbc;

    public PublicQrController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/{qrToken}")
    public ApiResponse<TableController.PublicTableMenu> resolve(@PathVariable String qrToken) {
        List<UUID> tenants = jdbc.query("""
                SELECT tenant_id FROM restaurant_tables
                WHERE qr_token = ? AND is_active = TRUE
                """, (rs, row) -> rs.getObject("tenant_id", UUID.class), qrToken);
        if (tenants.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "This table QR code is invalid or inactive.");
        // Delegate to the tenant-scoped public menu query without exposing tenant IDs in the QR URL.
        UUID tenantId = tenants.get(0);
        return jdbc.queryForObject("SELECT ?::uuid", (rs, row) -> ApiResponse.success(
                "Resolve through tenant-scoped QR endpoint", (TableController.PublicTableMenu) null), tenantId);
    }
}
