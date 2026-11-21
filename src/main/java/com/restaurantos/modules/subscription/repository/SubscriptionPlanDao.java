package com.restaurantos.modules.subscription.repository;
import com.restaurantos.modules.subscription.entity.SubscriptionPlan;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;
@Repository
public class SubscriptionPlanDao {
 private final JdbcTemplate jdbc;
 public SubscriptionPlanDao(JdbcTemplate jdbc){this.jdbc=jdbc;}
 private static final String SELECT="SELECT id,name,description,monthly_price_paise,annual_price_paise,status,created_at FROM platform_subscription_plans";
 private SubscriptionPlan map(java.sql.ResultSet rs,int row)throws java.sql.SQLException{
  return new SubscriptionPlan(rs.getObject("id",UUID.class),rs.getString("name"),rs.getString("description"),rs.getLong("monthly_price_paise"),rs.getLong("annual_price_paise"),rs.getString("status"),rs.getObject("created_at",java.time.OffsetDateTime.class));
 }
 public List<SubscriptionPlan> findAll(){return jdbc.query(SELECT+" ORDER BY created_at DESC,id DESC",this::map);}
 public SubscriptionPlan findById(UUID id){return jdbc.query(SELECT+" WHERE id=?",this::map,id).stream().findFirst().orElse(null);}
 public UUID insert(String name,String description,long monthly,long annual){return jdbc.queryForObject("INSERT INTO platform_subscription_plans(name,description,monthly_price_paise,annual_price_paise) VALUES(?,?,?,?) RETURNING id",UUID.class,name,description,monthly,annual);}
 public int updateDraft(UUID id,String name,String description,long monthly,long annual){return jdbc.update("UPDATE platform_subscription_plans SET name=?,description=?,monthly_price_paise=?,annual_price_paise=?,updated_at=NOW() WHERE id=? AND status='DRAFT'",name,description,monthly,annual,id);}
 public int changeStatus(UUID id,String from,String to){return jdbc.update("UPDATE platform_subscription_plans SET status=?,updated_at=NOW() WHERE id=? AND status=?",to,id,from);}
 public boolean hasSubscriptions(UUID id){return Boolean.TRUE.equals(jdbc.queryForObject(
  "SELECT EXISTS(SELECT 1 FROM platform_restaurant_subscriptions WHERE plan_id=?)",Boolean.class,id));}
 public int updateArchivedUnused(UUID id,String name,String description,long monthly,long annual){
  return jdbc.update("UPDATE platform_subscription_plans SET name=?,description=?,monthly_price_paise=?,annual_price_paise=?,updated_at=NOW() WHERE id=? AND status='ARCHIVED' AND NOT EXISTS(SELECT 1 FROM platform_restaurant_subscriptions WHERE plan_id=?)",name,description,monthly,annual,id,id);
 }
 public int archive(UUID id){return jdbc.update("UPDATE platform_subscription_plans SET status='ARCHIVED',updated_at=NOW() WHERE id=? AND status IN ('DRAFT','ACTIVE')",id);}
}
