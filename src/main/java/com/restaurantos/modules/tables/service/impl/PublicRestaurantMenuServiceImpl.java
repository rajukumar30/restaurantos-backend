package com.restaurantos.modules.tables.service.impl;
import com.restaurantos.modules.tables.dto.PublicRestaurantDto.RestaurantMenu;
import com.restaurantos.modules.tables.repository.PublicRestaurantMenuDao;
import com.restaurantos.modules.tables.service.PublicRestaurantMenuService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
@Service
public class PublicRestaurantMenuServiceImpl implements PublicRestaurantMenuService {
 private final PublicRestaurantMenuDao dao;
 public PublicRestaurantMenuServiceImpl(PublicRestaurantMenuDao dao){this.dao=dao;}
 @Transactional(readOnly=true)
 public RestaurantMenu getMenu(String slug,UUID branchId){return dao.getMenu(slug,branchId);}
}
