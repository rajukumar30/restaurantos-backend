package com.restaurantos.modules.branding.service;
import com.restaurantos.modules.branding.dto.BrandingSettings;
import org.springframework.security.core.Authentication;
import java.util.UUID;
import org.springframework.web.multipart.MultipartFile;
import java.util.Optional;
public interface BrandingService {
 BrandingSettings get(UUID tenantId,Authentication authentication);
 Optional<byte[]> image(UUID id);
 BrandingSettings publishWithImages(UUID tenantId,BrandingSettings settings,MultipartFile logo,MultipartFile banner,Authentication auth);
 BrandingSettings publish(UUID tenantId,BrandingSettings settings,Authentication authentication);
}
