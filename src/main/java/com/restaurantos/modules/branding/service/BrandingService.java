package com.restaurantos.modules.branding.service;
import com.restaurantos.modules.branding.dto.BrandingSettings;
import org.springframework.security.core.Authentication;
import java.util.UUID;
public interface BrandingService {
 BrandingSettings get(UUID tenantId,Authentication authentication);
 BrandingSettings publish(UUID tenantId,BrandingSettings settings,Authentication authentication);
}
