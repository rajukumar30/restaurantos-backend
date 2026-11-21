package com.restaurantos.modules.platform.service.impl;

import com.restaurantos.modules.platform.PlatformAnalyticsDao;
import com.restaurantos.modules.platform.dto.PlatformAnalyticsDto.Overview;
import com.restaurantos.modules.platform.service.PlatformAnalyticsService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlatformAnalyticsServiceImpl implements PlatformAnalyticsService {
    private final PlatformAnalyticsDao dao;
    public PlatformAnalyticsServiceImpl(PlatformAnalyticsDao dao) { this.dao=dao; }
    @Override @Transactional(readOnly=true)
    public Overview overview() { return dao.overview(); }
}
