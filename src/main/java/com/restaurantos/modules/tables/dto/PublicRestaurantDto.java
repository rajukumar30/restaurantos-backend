package com.restaurantos.modules.tables.dto;
import java.util.List;
import com.restaurantos.modules.branding.dto.BrandingSettings;
import java.util.UUID;
public final class PublicRestaurantDto {
 private PublicRestaurantDto(){}
    public record RestaurantHeader(UUID id, String name, String slug) {}
    public record BranchSummary(UUID id, String name) {}
    public record RestaurantMenu(String restaurantName, String slug, List<BranchSummary> branches,
                                 UUID branchId, String branchName, List<MenuCategory> categories, BrandingSettings branding) {
 public RestaurantMenu(String restaurantName,String slug,List<BranchSummary> branches,UUID branchId,String branchName,List<MenuCategory> categories){this(restaurantName,slug,branches,branchId,branchName,categories,BrandingSettings.defaults(restaurantName));}
 }
    public record MenuCategory(UUID id, String name, String description, int sortOrder, List<MenuItem> items) {}
    public record MenuItem(UUID id, String name, String description, java.math.BigDecimal price,
                           String dietaryType, int prepTimeMinutes,String imageUrl,boolean available) {
 public MenuItem(UUID id,String name,String description,java.math.BigDecimal price,String dietaryType,int prepTimeMinutes,String imageUrl){this(id,name,description,price,dietaryType,prepTimeMinutes,imageUrl,true);}
 public MenuItem(UUID id,String name,String description,java.math.BigDecimal price,String dietaryType,int prepTimeMinutes){this(id,name,description,price,dietaryType,prepTimeMinutes,null,true);}
 }
}
