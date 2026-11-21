package com.restaurantos.modules.platform.dto;
import java.util.*;
public final class PlatformPlanFeatureDto {
 private PlatformPlanFeatureDto(){}
 public record PlanFeature(String key,String name,String category,boolean available,boolean included,List<String> dependencies){}
 public record Update(boolean included){}
}
