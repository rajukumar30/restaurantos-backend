package com.restaurantos.modules.tables.service.impl;
import com.restaurantos.modules.tables.dto.PublicRestaurantDto.RestaurantMenu;
import com.restaurantos.modules.tables.repository.PublicRestaurantMenuDao;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class PublicRestaurantMenuServiceImplTest {
 @Test void delegatesRestaurantAndBranchSelection(){
  PublicRestaurantMenuDao dao=mock(PublicRestaurantMenuDao.class);
  RestaurantMenu expected=new RestaurantMenu("Cafe","cafe",List.of(),null,null,List.of());
  when(dao.getMenu("cafe",null)).thenReturn(expected);
  assertSame(expected,new PublicRestaurantMenuServiceImpl(dao).getMenu("cafe",null));
  verify(dao).getMenu("cafe",null);
 }
}
