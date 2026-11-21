package com.restaurantos.modules.platform.service;
import com.restaurantos.common.api.ApiResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;


import com.restaurantos.modules.platform.dto.PlatformAnalyticsDto.*;
public interface PlatformAnalyticsService {Overview overview();}
