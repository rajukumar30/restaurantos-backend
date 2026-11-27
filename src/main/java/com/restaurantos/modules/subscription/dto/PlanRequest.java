package com.restaurantos.modules.subscription.dto;
public record PlanRequest(String name,String description,Long monthlyPricePaise,Long annualPricePaise) {}
