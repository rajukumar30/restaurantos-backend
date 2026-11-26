package com.restaurantos.modules.tables.dto;
import java.util.List;
import com.restaurantos.modules.branding.dto.BrandingSettings;
import java.util.UUID;
import java.math.BigDecimal;
public final class PublicMenuDto {
 private PublicMenuDto(){}

    public record PublicMenu(UUID tableId, String tableName, UUID branchId, String branchName,
                             String restaurantName, List<PublicCategory> categories, String slogan, String logoUrl, String bannerUrl, String theme, BrandingSettings branding) {
 public PublicMenu(UUID tableId,String tableName,UUID branchId,String branchName,String restaurantName,List<PublicCategory> categories,String slogan,String logoUrl,String bannerUrl,String theme){this(tableId,tableName,branchId,branchName,restaurantName,categories,slogan,logoUrl,bannerUrl,theme,BrandingSettings.legacy(restaurantName,slogan,logoUrl,bannerUrl,theme));}
 }
    public record PublicCategory(UUID id, String name, List<PublicItem> items) {}
    public record PublicItem(UUID id, String name, String description, java.math.BigDecimal price,
                             String dietaryType, int prepTimeMinutes,String imageUrl) {
 public PublicItem(UUID id,String name,String description,BigDecimal price,String dietaryType,int prepTimeMinutes){this(id,name,description,price,dietaryType,prepTimeMinutes,null);}
 }
}
