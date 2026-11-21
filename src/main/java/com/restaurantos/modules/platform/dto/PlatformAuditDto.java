package com.restaurantos.modules.platform.dto;
import com.restaurantos.common.api.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;


public final class PlatformAuditDto {
 private PlatformAuditDto(){}
    public record AuditEvent(UUID id, UUID actorUserId, String actorName,
                             UUID tenantId, String restaurantName, String action,
                             String featureKey, Boolean oldValue, Boolean newValue,
                             OffsetDateTime createdAt) {}


}
