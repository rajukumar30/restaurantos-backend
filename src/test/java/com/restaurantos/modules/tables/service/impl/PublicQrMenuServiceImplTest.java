package com.restaurantos.modules.tables.service.impl;
import com.restaurantos.modules.tables.dto.PublicMenuDto.PublicMenu;
import com.restaurantos.modules.tables.repository.PublicQrMenuDao;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class PublicQrMenuServiceImplTest {
 @Test void delegatesQrLookupWithoutChangingResponse(){
  PublicQrMenuDao dao=mock(PublicQrMenuDao.class);
  PublicMenu menu=new PublicMenu(null,"Table 1",null,"Main","Restaurant",List.of(),"","","","CLASSIC");
  when(dao.resolve("qr-123")).thenReturn(menu);
  PublicQrMenuServiceImpl service=new PublicQrMenuServiceImpl(dao);
  assertSame(menu,service.resolve("qr-123"));
  verify(dao).resolve("qr-123");
 }
}
