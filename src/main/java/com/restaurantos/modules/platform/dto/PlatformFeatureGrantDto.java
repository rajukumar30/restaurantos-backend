package com.restaurantos.modules.platform.dto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.UUID;
public final class PlatformFeatureGrantDto {
 private PlatformFeatureGrantDto(){}
 public record Create(@NotBlank String featureKey,@NotNull OffsetDateTime expiresAt){}
 public record Grant(UUID id,UUID tenantId,String featureKey,OffsetDateTime startsAt,
  OffsetDateTime expiresAt,OffsetDateTime revokedAt,UUID grantedBy){}
}
