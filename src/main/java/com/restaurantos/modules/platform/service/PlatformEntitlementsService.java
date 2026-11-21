package com.restaurantos.modules.platform.service;
import com.restaurantos.modules.platform.dto.PlatformEntitlementsDto.Snapshot;
import java.util.UUID;
public interface PlatformEntitlementsService {Snapshot snapshot(UUID tenantId);}
