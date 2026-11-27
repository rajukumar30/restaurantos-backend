package com.restaurantos.modules.subscription.service.impl;
import com.restaurantos.modules.subscription.dto.PlanRequest;
import com.restaurantos.modules.subscription.entity.SubscriptionPlan;
import com.restaurantos.modules.subscription.repository.SubscriptionPlanDao;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.time.OffsetDateTime;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class PlanServiceImplTest {
 private final SubscriptionPlanDao dao=mock(SubscriptionPlanDao.class);
 private final PlanServiceImpl service=new PlanServiceImpl(dao);
 @Test void rejectsNegativePriceBeforeDatabaseWrite(){
  assertThrows(ResponseStatusException.class,()->service.create(new PlanRequest("Free","", -1L,0L)));
  verifyNoInteractions(dao);
 }
 @Test void createsFreeDraftPlan(){
  UUID id=UUID.randomUUID();
  SubscriptionPlan plan=new SubscriptionPlan(id,"Free","Starter",0,0,"DRAFT",OffsetDateTime.now());
  when(dao.insert("Free","Starter",0,0)).thenReturn(id);
  when(dao.findById(id)).thenReturn(plan);
  assertEquals("DRAFT",service.create(new PlanRequest(" Free ","Starter",0L,0L)).status());
 }
 @Test void refusesEditingNonDraftPlan(){
  UUID id=UUID.randomUUID();
  when(dao.updateDraft(id,"Basic","",100L,1000L)).thenReturn(0);
  assertThrows(ResponseStatusException.class,()->service.update(id,new PlanRequest("Basic","",100L,1000L)));
 }
 @Test void cannotActivateAlreadyActivePlan(){
  UUID id=UUID.randomUUID();
  when(dao.changeStatus(id,"DRAFT","ACTIVE")).thenReturn(0);
  assertThrows(ResponseStatusException.class,()->service.activate(id));
 }
}
