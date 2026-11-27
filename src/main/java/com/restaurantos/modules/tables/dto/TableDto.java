package com.restaurantos.modules.tables.dto;
import jakarta.validation.constraints.*;
import java.util.*;
public final class TableDto {
 private TableDto(){}
    public record CreateTableRequest(@NotNull UUID branchId,
                                     @NotBlank @Size(max = 30) String tableNumber,
                                     @Size(max = 80) String displayName,
                                     @Min(1) @Max(50) int seatingCapacity) {}
    public record UpdateTableRequest(@NotNull UUID branchId,
                                     @NotBlank @Size(max = 80) String displayName,
                                     @Min(1) @Max(50) int seatingCapacity,
                                     boolean active) {}
    public record RestaurantTable(UUID id, String tableNumber, String displayName,
                                  int seatingCapacity, String qrToken, boolean active) {}
    public record PublicTableMenu(UUID tableId, String tableName, UUID branchId,
                                  String branchName, String restaurantName,
                                  List<PublicMenuCategory> categories) {}
    public record PublicMenuCategory(UUID id, String name, List<PublicMenuItem> items) {}
    public record PublicMenuItem(UUID id, String name, String description,
                                 java.math.BigDecimal price, String dietaryType,
                                 int prepTimeMinutes) {}
}
