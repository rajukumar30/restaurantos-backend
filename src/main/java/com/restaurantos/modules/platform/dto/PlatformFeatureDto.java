package com.restaurantos.modules.platform.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;

/** Existing feature-catalogue JSON representation. */
public final class PlatformFeatureDto {
    private PlatformFeatureDto() {}
    public record Feature(String key, String name, String category, String description,
                          boolean available, boolean enabled, Boolean overrideEnabled,
                          List<String> dependencies) {}
    public record UpdateFeature(@NotNull Boolean enabled) {}
}
