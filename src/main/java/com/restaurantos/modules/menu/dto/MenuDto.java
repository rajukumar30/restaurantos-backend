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
                                        @jakarta.validation.constraints.Max(240) Integer prepTimeMinutes) {}
    public record UpdateMenuItemRequest(@NotNull UUID branchId,
                                        @NotNull @DecimalMin("0.01") BigDecimal price,
                                        boolean available) {}
    public record MenuCategory(UUID id, String name, String description, int sortOrder,
                               boolean active, List<MenuItem> items) {}
    public record MenuItem(UUID id, UUID categoryId, String name, String description,
                           BigDecimal price, String dietaryType, int prepTimeMinutes,
                           boolean available) {}
    public record CategoryCreated(UUID id, String name) {}
    public record ItemCreated(UUID id, UUID categoryId, String name, BigDecimal price, String dietaryType) {}
    public record ItemUpdated(UUID id, BigDecimal price, boolean available) {}
}
