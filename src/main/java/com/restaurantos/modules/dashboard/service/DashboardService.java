package com.restaurantos.modules.dashboard.service;
import com.restaurantos.modules.dashboard.dto.DashboardData;
import org.springframework.security.core.Authentication;
import java.util.UUID;
public interface DashboardService { DashboardData load(UUID tenantId,UUID branchId,Authentication authentication); }
