package com.restaurantos.modules.subscription.service;
import com.restaurantos.modules.subscription.dto.PlanRequest;
import com.restaurantos.modules.subscription.entity.SubscriptionPlan;
import java.util.List;
import java.util.UUID;
public interface PlanService {
 List<SubscriptionPlan> list();
 SubscriptionPlan create(PlanRequest request);
 SubscriptionPlan update(UUID id,PlanRequest request);
 SubscriptionPlan activate(UUID id);
 SubscriptionPlan deactivate(UUID id);
 SubscriptionPlan reactivate(UUID id);
 SubscriptionPlan createVersion(UUID id);
}
