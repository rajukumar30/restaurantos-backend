package com.restaurantos.modules.platform.service.impl;
import com.restaurantos.common.api.ApiResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;


import com.restaurantos.modules.platform.dto.PlatformAnalyticsDto.*;
import com.restaurantos.modules.platform.service.PlatformAnalyticsService;
import com.restaurantos.modules.platform.PlatformAnalyticsDao;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class PlatformAnalyticsServiceImpl implements PlatformAnalyticsService {
 private final PlatformAnalyticsDao dao;
 public PlatformAnalyticsServiceImpl(PlatformAnalyticsDao dao){this.dao=dao;}
 @Transactional(readOnly=true)
         public Overview overview() { return dao.overview(); }

}
