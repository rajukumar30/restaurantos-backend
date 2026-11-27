package com.restaurantos.modules.tables.service;
import com.restaurantos.modules.tables.dto.PublicRestaurantDto.RestaurantMenu;
import java.util.UUID;
public interface PublicRestaurantMenuService {RestaurantMenu getMenu(String slug,UUID branchId);}
