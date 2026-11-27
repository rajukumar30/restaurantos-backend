package com.restaurantos.modules.subscription.entity;
import java.time.OffsetDateTime;
import java.util.UUID;
public record SubscriptionPlan(UUID id,String name,String description,long monthlyPricePaise,long annualPricePaise,String status,OffsetDateTime createdAt) {}
