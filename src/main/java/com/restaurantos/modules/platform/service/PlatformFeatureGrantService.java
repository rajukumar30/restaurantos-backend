package com.restaurantos.modules.platform.service;
import com.restaurantos.modules.platform.dto.PlatformFeatureGrantDto.*;
import org.springframework.security.core.Authentication;
import java.util.*;
public interface PlatformFeatureGrantService {
 List<Grant> list(UUID tenant);
 List<Grant> create(UUID tenant,Create request,Authentication auth);
 List<Grant> revoke(UUID tenant,UUID grant,Authentication auth);
}
