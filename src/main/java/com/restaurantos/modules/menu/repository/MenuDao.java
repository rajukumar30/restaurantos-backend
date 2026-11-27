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
          mi.dietary_type,mi.prep_time_minutes,bmi.is_available, '/api/v1/public/menu/images/' || CAST(img.id AS text) AS image_url
   FROM menu_items mi JOIN branch_menu_items bmi
     ON bmi.tenant_id=mi.tenant_id AND bmi.menu_item_id=mi.id
   LEFT JOIN menu_item_images img ON img.tenant_id=mi.tenant_id AND img.item_id=mi.id
   WHERE mi.tenant_id=? AND bmi.branch_id=? AND mi.is_active=TRUE
   ORDER BY mi.name
   """,rs->{
    UUID categoryId=rs.getObject("category_id",UUID.class);
    itemsByCategory.computeIfAbsent(categoryId,ignored->new ArrayList<>()).add(
     new MenuItem(rs.getObject("id",UUID.class),categoryId,rs.getString("name"),
      rs.getString("description"),rs.getBigDecimal("price"),rs.getString("dietary_type"),
      rs.getInt("prep_time_minutes"),rs.getBoolean("is_available"),rs.getString("image_url")));
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
 public boolean assignedItemExists(UUID tenantId,UUID branchId,UUID itemId){
  return Boolean.TRUE.equals(jdbc.queryForObject("""
   SELECT EXISTS(SELECT 1 FROM menu_items mi JOIN branch_menu_items bmi ON bmi.tenant_id=mi.tenant_id AND bmi.menu_item_id=mi.id
    WHERE mi.tenant_id=? AND bmi.branch_id=? AND mi.id=? AND mi.is_active=TRUE)
   """,Boolean.class,tenantId,branchId,itemId));
 }
 public String storeImage(UUID tenantId,UUID itemId,byte[] bytes,byte[] thumbnail){
  UUID id=UUID.randomUUID();
  jdbc.update("""
   INSERT INTO menu_item_images(id,tenant_id,item_id,image_bytes,thumbnail_bytes) VALUES(?,?,?,?,?)
   ON CONFLICT(item_id) DO UPDATE SET id=EXCLUDED.id,image_bytes=EXCLUDED.image_bytes,thumbnail_bytes=EXCLUDED.thumbnail_bytes WHERE menu_item_images.tenant_id=EXCLUDED.tenant_id
   """,id,tenantId,itemId,bytes,thumbnail);return "/api/v1/public/menu/images/"+id;
 }
 public void removeImage(UUID tenantId,UUID itemId){jdbc.update("DELETE FROM menu_item_images WHERE tenant_id=? AND item_id=?",tenantId,itemId);}
 public Optional<byte[]> publicImage(UUID id,boolean thumbnail){
  return jdbc.query("""
   SELECT CASE WHEN ? THEN COALESCE(img.thumbnail_bytes,img.image_bytes) ELSE img.image_bytes END FROM menu_item_images img JOIN tenants t ON t.id=img.tenant_id AND t.status='ACTIVE'
   JOIN menu_items mi ON mi.tenant_id=img.tenant_id AND mi.id=img.item_id AND mi.is_active=TRUE
   JOIN menu_categories mc ON mc.tenant_id=mi.tenant_id AND mc.id=mi.category_id AND mc.is_active=TRUE
   WHERE img.id=? AND EXISTS(SELECT 1 FROM branch_menu_items bmi JOIN branches b ON b.id=bmi.branch_id AND b.tenant_id=bmi.tenant_id
     WHERE bmi.tenant_id=mi.tenant_id AND bmi.menu_item_id=mi.id AND b.status='ACTIVE')
   """,(rs,row)->rs.getBytes(1),thumbnail,id).stream().findFirst();
 }
 private static final String CATALOG_FROM="""
 FROM menu_items mi JOIN branch_menu_items bmi ON bmi.tenant_id=mi.tenant_id AND bmi.menu_item_id=mi.id
 JOIN menu_categories mc ON mc.tenant_id=mi.tenant_id AND mc.id=mi.category_id
 LEFT JOIN menu_item_images img ON img.tenant_id=mi.tenant_id AND img.item_id=mi.id
 WHERE mi.tenant_id=? AND bmi.branch_id=? AND mi.is_active=TRUE AND mc.is_active=TRUE
 """;
 private static final String CATALOG_SELECT="""
 SELECT mi.id,mi.category_id,mc.name AS category_name,mi.name,mi.description,
 COALESCE(bmi.price_override,mi.base_price) AS price,mi.dietary_type,mi.prep_time_minutes,bmi.is_available,
 '/api/v1/public/menu/images/' || CAST(img.id AS text) AS image_url
 """;
 private static CatalogItem catalogItem(java.sql.ResultSet rs,int row)throws java.sql.SQLException{
  return new CatalogItem(rs.getObject("id",UUID.class),rs.getObject("category_id",UUID.class),rs.getString("category_name"),rs.getString("name"),rs.getString("description"),rs.getBigDecimal("price"),rs.getString("dietary_type"),rs.getInt("prep_time_minutes"),rs.getBoolean("is_available"),rs.getString("image_url"));
 }
 public CatalogPage catalog(UUID tenantId,CatalogQuery q){
  var categories=jdbc.query("""
   SELECT mc.id,mc.name,mc.is_active,COUNT(bmi.menu_item_id) AS item_count FROM menu_categories mc
   LEFT JOIN menu_items mi ON mi.tenant_id=mc.tenant_id AND mi.category_id=mc.id AND mi.is_active=TRUE
   LEFT JOIN branch_menu_items bmi ON bmi.tenant_id=mi.tenant_id AND bmi.menu_item_id=mi.id AND bmi.branch_id=?
   WHERE mc.tenant_id=? GROUP BY mc.id,mc.name,mc.is_active,mc.sort_order ORDER BY mc.sort_order,mc.name
   """,(rs,row)->new CategorySummary(rs.getObject("id",UUID.class),rs.getString("name"),rs.getBoolean("is_active"),rs.getLong("item_count")),q.branchId(),tenantId);
  var stats=jdbc.queryForObject("SELECT COUNT(*) AS total,COUNT(*) FILTER(WHERE bmi.is_available) AS available,COUNT(*) FILTER(WHERE NOT bmi.is_available) AS sold_out,COUNT(*) FILTER(WHERE img.id IS NULL) AS no_photo "+CATALOG_FROM,(rs,row)->new CatalogStats(rs.getLong("total"),rs.getLong("available"),rs.getLong("sold_out"),rs.getLong("no_photo")),tenantId,q.branchId());
  var where=new StringBuilder(CATALOG_FROM);var args=new ArrayList<Object>(List.of(tenantId,q.branchId()));
  if(!q.search().isBlank()){where.append(" AND (strpos(lower(mi.name),lower(?))>0 OR strpos(lower(COALESCE(mi.description,'')),lower(?))>0 OR strpos(lower(mc.name),lower(?))>0)");args.add(q.search());args.add(q.search());args.add(q.search());}
  if(q.categoryId()!=null){where.append(" AND mi.category_id=?");args.add(q.categoryId());}
  if(!"all".equals(q.availability())){where.append(" AND bmi.is_available=?");args.add("available".equals(q.availability()));}
  if(!"all".equals(q.diet())){where.append(" AND mi.dietary_type=?");args.add(q.diet());}
  if(!"all".equals(q.photo()))where.append("yes".equals(q.photo())?" AND img.id IS NOT NULL":" AND img.id IS NULL");
  if(q.minPrice()!=null){where.append(" AND COALESCE(bmi.price_override,mi.base_price)>=?");args.add(q.minPrice());}
  if(q.maxPrice()!=null){where.append(" AND COALESCE(bmi.price_override,mi.base_price)<=?");args.add(q.maxPrice());}
  long total=jdbc.queryForObject("SELECT COUNT(*) "+where,Long.class,args.toArray());
  int page=Math.min(q.page(),Math.max(1,(int)Math.ceil((double)total/q.size())));
  String sort=switch(q.sort()){case "price-low"->"price ASC,mi.name";case "price-high"->"price DESC,mi.name";case "recent"->"GREATEST(mi.updated_at,bmi.updated_at) DESC";case "name-desc"->"lower(mi.name) DESC";default->"lower(mi.name) ASC";};
  args.add(q.size());args.add((long)(page-1)*q.size());
  var rows=jdbc.query(CATALOG_SELECT+where+" ORDER BY "+(q.grouped()?"mc.sort_order,mc.name,":"")+sort+",mi.id LIMIT ? OFFSET ?",MenuDao::catalogItem,args.toArray());
  return new CatalogPage(categories,stats,rows,total,page,q.size());
 }
 public Optional<CatalogItem> catalogItem(UUID tenant,UUID branch,UUID item){
  return jdbc.query(CATALOG_SELECT+CATALOG_FROM+" AND mi.id=?",MenuDao::catalogItem,tenant,branch,item).stream().findFirst();
 }
 public List<UUID> assignedBranches(UUID tenant,UUID item){
  return jdbc.query("SELECT branch_id FROM branch_menu_items WHERE tenant_id=? AND menu_item_id=?",(rs,row)->rs.getObject(1,UUID.class),tenant,item);
 }
 public int editDefinition(UUID tenant,UUID item,CreateMenuItemRequest request,String diet){
  return jdbc.update("UPDATE menu_items SET category_id=?,name=?,description=?,dietary_type=?,prep_time_minutes=?,updated_at=NOW() WHERE tenant_id=? AND id=? AND is_active=TRUE",request.categoryId(),request.name().trim(),request.description(),diet,request.prepTimeMinutes(),tenant,item);
 }
 public int setAvailable(UUID tenant,UUID branch,UUID item,boolean available){
  return jdbc.update("""
   UPDATE branch_menu_items bmi SET is_available=?,updated_at=NOW()
   FROM menu_items mi WHERE bmi.tenant_id=? AND bmi.branch_id=? AND bmi.menu_item_id=? AND mi.tenant_id=bmi.tenant_id AND mi.id=bmi.menu_item_id AND mi.is_active=TRUE
   """,available,tenant,branch,item);
 }
 public int archive(UUID tenant,UUID item){
  return jdbc.update("UPDATE menu_items SET is_active=FALSE,updated_at=NOW() WHERE tenant_id=? AND id=? AND is_active=TRUE",tenant,item);
 }
 public int move(UUID tenant,UUID item,UUID category){
  return jdbc.update("UPDATE menu_items SET category_id=?,updated_at=NOW() WHERE tenant_id=? AND id=? AND is_active=TRUE",category,tenant,item);
 }
 public void copyPhoto(UUID tenant,UUID source,UUID target){
  // Only copy already optimized variants; legacy files remain on the original item.
  jdbc.update("""
   INSERT INTO menu_item_images(id,tenant_id,item_id,image_bytes,thumbnail_bytes)
   SELECT ?,tenant_id,?,image_bytes,thumbnail_bytes FROM menu_item_images WHERE tenant_id=? AND item_id=? AND octet_length(image_bytes)<=102400 AND thumbnail_bytes IS NOT NULL AND octet_length(thumbnail_bytes)<=40960
   """,UUID.randomUUID(),target,tenant,source);
 }
}
