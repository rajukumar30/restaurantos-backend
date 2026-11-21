package com.restaurantos.modules.menu.repository;
import com.restaurantos.modules.menu.dto.MenuDto.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.*;
@Repository
public class MenuDao {
 private final JdbcTemplate jdbc;
 public MenuDao(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public List<MenuCategory> findMenu(UUID tenantId,UUID branchId){
  Map<UUID,List<MenuItem>> itemsByCategory=new LinkedHashMap<>();
  jdbc.query("""
   SELECT mi.id,mi.category_id,mi.name,mi.description,
          COALESCE(bmi.price_override,mi.base_price) AS price,
          mi.dietary_type,mi.prep_time_minutes,bmi.is_available
   FROM menu_items mi JOIN branch_menu_items bmi
     ON bmi.tenant_id=mi.tenant_id AND bmi.menu_item_id=mi.id
   WHERE mi.tenant_id=? AND bmi.branch_id=? AND mi.is_active=TRUE
   ORDER BY mi.name
   """,rs->{
    UUID categoryId=rs.getObject("category_id",UUID.class);
    itemsByCategory.computeIfAbsent(categoryId,ignored->new ArrayList<>()).add(
     new MenuItem(rs.getObject("id",UUID.class),categoryId,rs.getString("name"),
      rs.getString("description"),rs.getBigDecimal("price"),rs.getString("dietary_type"),
      rs.getInt("prep_time_minutes"),rs.getBoolean("is_available")));
   },tenantId,branchId);
  return jdbc.query("""
   SELECT id,name,description,sort_order,is_active FROM menu_categories
   WHERE tenant_id=? ORDER BY sort_order,name
   """,(rs,row)->{
    UUID id=rs.getObject("id",UUID.class);
    return new MenuCategory(id,rs.getString("name"),rs.getString("description"),
     rs.getInt("sort_order"),rs.getBoolean("is_active"),itemsByCategory.getOrDefault(id,List.of()));
   },tenantId);
 }
 public boolean categoryNameExists(UUID tenantId,String name){
  Integer count=jdbc.queryForObject("SELECT COUNT(*) FROM menu_categories WHERE tenant_id=? AND lower(name)=lower(?)",Integer.class,tenantId,name);
  return count!=null&&count>0;
 }
 public void insertCategory(UUID id,UUID tenantId,String name,String description,int sortOrder){
  jdbc.update("INSERT INTO menu_categories(id,tenant_id,name,description,sort_order) VALUES(?,?,?,?,?)",id,tenantId,name,description,sortOrder);
 }
 public boolean activeCategoryExists(UUID tenantId,UUID categoryId){
  Integer count=jdbc.queryForObject("SELECT COUNT(*) FROM menu_categories WHERE tenant_id=? AND id=? AND is_active=TRUE",Integer.class,tenantId,categoryId);
  return count!=null&&count>0;
 }
 public void insertItem(UUID id,UUID tenantId,CreateMenuItemRequest request,String dietaryType){
  jdbc.update("""
   INSERT INTO menu_items(id,tenant_id,category_id,name,description,base_price,dietary_type,prep_time_minutes)
   VALUES(?,?,?,?,?,?,?,?)
   """,id,tenantId,request.categoryId(),request.name().trim(),request.description(),request.price(),dietaryType,request.prepTimeMinutes());
 }
 public void assignItemToBranch(UUID tenantId,UUID branchId,UUID itemId){
  jdbc.update("INSERT INTO branch_menu_items(tenant_id,branch_id,menu_item_id) VALUES(?,?,?)",tenantId,branchId,itemId);
 }
 public int updateBranchItem(UUID tenantId,UUID branchId,UUID itemId,UpdateMenuItemRequest request){
  return jdbc.update("""
   UPDATE branch_menu_items SET price_override=?,is_available=?,updated_at=NOW()
   WHERE tenant_id=? AND branch_id=? AND menu_item_id=?
   """,request.price(),request.available(),tenantId,branchId,itemId);
 }
}
