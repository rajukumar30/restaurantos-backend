package com.restaurantos.modules.tables;
import com.restaurantos.common.api.ApiResponse;
import com.restaurantos.modules.tables.dto.PublicRestaurantDto.RestaurantMenu;
import com.restaurantos.modules.tables.service.PublicRestaurantMenuService;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
@RestController
@RequestMapping("/api/v1/public/restaurants")
public class PublicRestaurantController {
 private final PublicRestaurantMenuService service;
 public PublicRestaurantController(PublicRestaurantMenuService service){this.service=service;}
 @GetMapping("/{slug}/menu")
 public ApiResponse<RestaurantMenu> getMenu(@PathVariable String slug,@RequestParam(required=false) UUID branchId){
  return ApiResponse.success("Restaurant menu loaded",service.getMenu(slug,branchId));
 }
}
