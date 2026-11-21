package com.restaurantos.modules.platform.service;

import com.restaurantos.modules.platform.dto.PlatformSubscriptionDto.*;
import org.springframework.security.core.Authentication;
import java.util.List;
import java.util.UUID;

public interface PlatformSubscriptionService {
    List<Subscription> list(UUID tenantId);
    Subscription assign(Assign request, Authentication authentication);
    Subscription cancel(UUID id, Authentication authentication);
}
