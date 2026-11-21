package com.restaurantos.modules.platform.service.impl;

import com.restaurantos.modules.platform.repository.PublicRestaurantDomainDao;
import com.restaurantos.modules.platform.service.PublicRestaurantDomainService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.util.Locale;

@Service
public class PublicRestaurantDomainServiceImpl implements PublicRestaurantDomainService {
    private final PublicRestaurantDomainDao dao;
    public PublicRestaurantDomainServiceImpl(PublicRestaurantDomainDao dao) { this.dao = dao; }

    @Override
    public String resolveSlug(String subdomain) {
        if (subdomain == null || !subdomain.matches("(?i)[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid restaurant subdomain");
        }
        return dao.findActiveSlugBySubdomain(subdomain.toLowerCase(Locale.ROOT))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Restaurant not found"));
    }
}
