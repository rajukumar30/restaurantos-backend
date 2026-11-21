package com.restaurantos.modules.subscription.service.impl;
import com.restaurantos.modules.subscription.dto.PlanRequest;
import com.restaurantos.modules.subscription.entity.SubscriptionPlan;
import com.restaurantos.modules.subscription.repository.SubscriptionPlanDao;
import com.restaurantos.modules.subscription.service.PlanService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.UUID;
@Service
public class PlanServiceImpl implements PlanService {
 private final SubscriptionPlanDao dao;
 public PlanServiceImpl(SubscriptionPlanDao dao){this.dao=dao;}
 public List<SubscriptionPlan> list(){return dao.findAll();}
 private PlanRequest validate(PlanRequest request){
  if(request==null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Plan details required");
  String name=request.name()==null?"":request.name().strip();
  String description=request.description()==null?"":request.description().strip();
  if(name.isBlank()||name.length()>100||description.length()>500||request.monthlyPricePaise()==null||request.annualPricePaise()==null||request.monthlyPricePaise()<0||request.annualPricePaise()<0)
   throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid plan details or pricing");
  return new PlanRequest(name,description,request.monthlyPricePaise(),request.annualPricePaise());
 }
 private SubscriptionPlan find(UUID id){
  SubscriptionPlan plan=dao.findById(id);
  if(plan==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Plan not found");
  return plan;
 }
 @Transactional
 public SubscriptionPlan create(PlanRequest raw){
  PlanRequest r=validate(raw);
  try{return find(dao.insert(r.name(),r.description(),r.monthlyPricePaise(),r.annualPricePaise()));}
  catch(DuplicateKeyException ex){throw new ResponseStatusException(HttpStatus.CONFLICT,"A plan with this name already exists");}
 }
 @Transactional
 public SubscriptionPlan update(UUID id,PlanRequest raw){
  PlanRequest r=validate(raw);
  try{
   SubscriptionPlan current=find(id);
   if("ARCHIVED".equals(current.status()) && dao.hasSubscriptions(id))
    throw new ResponseStatusException(HttpStatus.CONFLICT,"This plan has subscription history. Create a new draft to change its pricing.");
   int updated="ARCHIVED".equals(current.status())
    ?dao.updateArchivedUnused(id,r.name(),r.description(),r.monthlyPricePaise(),r.annualPricePaise())
    :dao.updateDraft(id,r.name(),r.description(),r.monthlyPricePaise(),r.annualPricePaise());
   if(updated==0)
    throw new ResponseStatusException(HttpStatus.CONFLICT,"Only draft plans or archived plans without subscriptions can be edited");
   return find(id);
  }catch(DuplicateKeyException ex){throw new ResponseStatusException(HttpStatus.CONFLICT,"A plan with this name already exists");}
 }
 @Transactional
 public SubscriptionPlan activate(UUID id){
  if(dao.changeStatus(id,"DRAFT","ACTIVE")==0)throw new ResponseStatusException(HttpStatus.CONFLICT,"Only draft plans can be activated");
  return find(id);
 }
 @Transactional
 public SubscriptionPlan createVersion(UUID id){
  SubscriptionPlan source=dao.lockById(id);
  if(source==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Plan not found");
  // A distinct plan ID preserves every existing subscription and its original entitlements.
  String suffix=" - Revision "+UUID.randomUUID().toString().substring(0,8);
  String base=source.name();
  String name=base.substring(0,Math.min(base.length(),100-suffix.length()))+suffix;
  try{
   UUID copy=dao.insert(name,source.description(),source.monthlyPricePaise(),source.annualPricePaise());
   dao.copyFeatures(id,copy);
   return find(copy);
  }catch(DuplicateKeyException ex){throw new ResponseStatusException(HttpStatus.CONFLICT,"Unable to generate unique plan revision");}
 }
 @Transactional
 public SubscriptionPlan reactivate(UUID id){
  if(dao.changeStatus(id,"ARCHIVED","ACTIVE")==0)
   throw new ResponseStatusException(HttpStatus.CONFLICT,"Only deactivated plans can be reactivated");
  return find(id);
 }
 @Transactional
 public SubscriptionPlan deactivate(UUID id){
  if(dao.archive(id)==0)throw new ResponseStatusException(HttpStatus.CONFLICT,"Plan not found or already deactivated");
  return find(id);
 }
}
