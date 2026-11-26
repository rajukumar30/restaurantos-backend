package com.restaurantos.modules.menu.dto;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.*;
public final class MenuDto {
 private MenuDto(){}
    public record CreateCategoryRequest(@NotNull UUID branchId,
                                        @NotBlank @Size(max=100) String name,
                                        @Size(max=300) String description,
                                        int sortOrder) {}
    public record CreateMenuItemRequest(@NotNull UUID branchId,
                                        @NotNull UUID categoryId,
                                        @NotBlank @Size(max=150) String name,
                                        @Size(max=500) String description,
                                        @NotNull @DecimalMin("0.01") BigDecimal price,
                                        @NotBlank String dietaryType,
                                        @NotNull @jakarta.validation.constraints.Min(1)
                                        @jakarta.validation.constraints.Max(240) Integer prepTimeMinutes, Boolean available) {
 public CreateMenuItemRequest(UUID branchId,UUID categoryId,String name,String description,BigDecimal price,String dietaryType,Integer prepTimeMinutes){this(branchId,categoryId,name,description,price,dietaryType,prepTimeMinutes,true);}
 public boolean initiallyAvailable(){return !Boolean.FALSE.equals(available);}
 }
    public record UpdateMenuItemRequest(@NotNull UUID branchId,
                                        @NotNull @DecimalMin("0.01") BigDecimal price,
                                        boolean available) {}
    public record MenuCategory(UUID id, String name, String description, int sortOrder,
                               boolean active, List<MenuItem> items) {}
    public record MenuItem(UUID id, UUID categoryId, String name, String description,
                           BigDecimal price, String dietaryType, int prepTimeMinutes,
                           boolean available, String imageUrl) {
 public MenuItem(UUID id,UUID categoryId,String name,String description,BigDecimal price,String dietaryType,int prepTimeMinutes,boolean available){this(id,categoryId,name,description,price,dietaryType,prepTimeMinutes,available,null);}
 }
    public record ImageUpdated(UUID itemId,String imageUrl) {}
    public record CategoryCreated(UUID id, String name) {}
    public record ItemCreated(UUID id, UUID categoryId, String name, BigDecimal price, String dietaryType) {}
    public record ItemUpdated(UUID id, BigDecimal price, boolean available) {}
 public record CategorySummary(UUID id,String name,boolean active,long count){}
 public record CatalogItem(UUID id,UUID categoryId,String categoryName,String name,String description,BigDecimal price,String dietaryType,int prepTimeMinutes,boolean available,String imageUrl){}
 public record CatalogStats(long total,long available,long soldOut,long noPhoto){}
 public record CatalogPage(List<CategorySummary> categories,CatalogStats stats,List<CatalogItem> items,long total,int page,int size){}
 public record CatalogQuery(UUID branchId,String search,UUID categoryId,String availability,String diet,String photo,BigDecimal minPrice,BigDecimal maxPrice,String sort,boolean grouped,int page,int size){}
 public record AvailabilityRequest(@NotNull UUID branchId,@NotNull Boolean available){}
 public record BulkRequest(@NotNull UUID branchId,@NotEmpty @Size(max=100) List<@NotNull UUID> itemIds,@NotBlank String action,UUID categoryId){}
 public record BulkResult(int updated){}
}
