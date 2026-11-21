package com.restaurantos.modules.tables.dto;
import java.util.List;
import java.util.UUID;
import java.math.BigDecimal;
public final class PublicMenuDto {
 private PublicMenuDto(){}

    public record PublicMenu(UUID tableId, String tableName, UUID branchId, String branchName,
                             String restaurantName, List<PublicCategory> categories, String slogan, String logoUrl, String bannerUrl, String theme) {}
    public record PublicCategory(UUID id, String name, List<PublicItem> items) {}
    public record PublicItem(UUID id, String name, String description, java.math.BigDecimal price,
                             String dietaryType, int prepTimeMinutes) {}
}
