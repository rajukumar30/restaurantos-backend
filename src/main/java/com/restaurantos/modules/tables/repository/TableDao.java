package com.restaurantos.modules.tables.repository;
import com.restaurantos.modules.tables.dto.TableDto.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.*;
@Repository
public class TableDao {
 private final JdbcTemplate jdbc;
 public TableDao(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public List<RestaurantTable> list(UUID tenantId,UUID branchId){
  return jdbc.query("""
   SELECT id,table_number,display_name,seating_capacity,qr_token,is_active
   FROM restaurant_tables WHERE tenant_id=? AND branch_id=? ORDER BY table_number
   """,(rs,row)->mapTable(rs),tenantId,branchId);
 }
 private RestaurantTable mapTable(java.sql.ResultSet rs)throws java.sql.SQLException{
  return new RestaurantTable(rs.getObject("id",UUID.class),rs.getString("table_number"),
   rs.getString("display_name"),rs.getInt("seating_capacity"),
   rs.getString("qr_token"),rs.getBoolean("is_active"));
 }
 public boolean numberExists(UUID tenantId,UUID branchId,String number){
  Integer count=jdbc.queryForObject("""
   SELECT COUNT(*) FROM restaurant_tables
   WHERE tenant_id=? AND branch_id=? AND lower(table_number)=lower(?)
   """,Integer.class,tenantId,branchId,number);
  return count!=null&&count>0;
 }
 public void insert(UUID id,UUID tenantId,UUID branchId,String number,String displayName,int capacity,String token){
  jdbc.update("""
   INSERT INTO restaurant_tables(id,tenant_id,branch_id,table_number,display_name,seating_capacity,qr_token)
   VALUES(?,?,?,?,?,?,?)
   """,id,tenantId,branchId,number,displayName,capacity,token);
 }
 public int update(UUID tenantId,UUID branchId,UUID tableId,UpdateTableRequest request){
  return jdbc.update("""
   UPDATE restaurant_tables SET display_name=?,seating_capacity=?,is_active=?,updated_at=NOW()
   WHERE tenant_id=? AND branch_id=? AND id=?
   """,request.displayName().trim(),request.seatingCapacity(),request.active(),tenantId,branchId,tableId);
 }
 public RestaurantTable find(UUID tenantId,UUID branchId,UUID tableId){
  return jdbc.queryForObject("""
   SELECT id,table_number,display_name,seating_capacity,qr_token,is_active
   FROM restaurant_tables WHERE tenant_id=? AND branch_id=? AND id=?
   """,(rs,row)->mapTable(rs),tenantId,branchId,tableId);
 }
 public PublicTableMenu publicMenu(UUID tenantId,String qrToken){
  List<PublicTableMenu> matches=jdbc.query("""
   SELECT rt.id AS table_id,rt.display_name AS table_name,
          b.id AS branch_id,b.name AS branch_name,t.name AS restaurant_name
   FROM restaurant_tables rt
   JOIN branches b ON b.id=rt.branch_id AND b.tenant_id=rt.tenant_id
   JOIN tenants t ON t.id=rt.tenant_id
   WHERE rt.tenant_id=? AND rt.qr_token=? AND rt.is_active=TRUE
     AND b.status='ACTIVE' AND t.status='ACTIVE'
   """,(rs,row)->new PublicTableMenu(rs.getObject("table_id",UUID.class),
    rs.getString("table_name"),rs.getObject("branch_id",UUID.class),
    rs.getString("branch_name"),rs.getString("restaurant_name"),List.of()),tenantId,qrToken);
  if(matches.isEmpty())return null;
  PublicTableMenu table=matches.getFirst();
  Map<UUID,List<PublicMenuItem>> grouped=new LinkedHashMap<>();
  jdbc.query("""
   SELECT mc.id AS category_id,mc.name AS category_name,mc.sort_order,
          mi.id AS item_id,mi.name AS item_name,mi.description,
          COALESCE(bmi.price_override,mi.base_price) AS price,mi.dietary_type,mi.prep_time_minutes
   FROM menu_categories mc
   JOIN menu_items mi ON mi.tenant_id=mc.tenant_id AND mi.category_id=mc.id
   JOIN branch_menu_items bmi ON bmi.tenant_id=mi.tenant_id AND bmi.menu_item_id=mi.id
   WHERE mc.tenant_id=? AND bmi.branch_id=? AND mc.is_active=TRUE
     AND mi.is_active=TRUE AND bmi.is_available=TRUE
   ORDER BY mc.sort_order,mc.name,mi.name
   """,rs->{
    UUID categoryId=rs.getObject("category_id",UUID.class);
    grouped.computeIfAbsent(categoryId,ignored->new ArrayList<>()).add(
     new PublicMenuItem(rs.getObject("item_id",UUID.class),rs.getString("item_name"),
      rs.getString("description"),rs.getBigDecimal("price"),rs.getString("dietary_type"),
      rs.getInt("prep_time_minutes")));
   },tenantId,table.branchId());
  List<PublicMenuCategory> categories=jdbc.query("""
   SELECT id,name,sort_order FROM menu_categories
   WHERE tenant_id=? AND is_active=TRUE ORDER BY sort_order,name
   """,(rs,row)->new PublicMenuCategory(rs.getObject("id",UUID.class),rs.getString("name"),
    grouped.getOrDefault(rs.getObject("id",UUID.class),List.of())),tenantId);
  return new PublicTableMenu(table.tableId(),table.tableName(),table.branchId(),
   table.branchName(),table.restaurantName(),categories);
 }
}
