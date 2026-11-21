package com.restaurantos.modules.platform.directory.service.impl;
import com.restaurantos.modules.platform.directory.repository.PlatformDirectoryDao;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class PlatformDirectoryServiceImplTest {
 private final PlatformDirectoryDao dao=mock(PlatformDirectoryDao.class);
 private final PlatformDirectoryServiceImpl service=new PlatformDirectoryServiceImpl(dao);
 @Test void rejectsNegativePage(){assertThrows(ResponseStatusException.class,()->service.memberships("",null,-1));verifyNoInteractions(dao);}
 @Test void rejectsLongSearch(){assertThrows(ResponseStatusException.class,()->service.memberships("x".repeat(101),null,0));verifyNoInteractions(dao);}
 @Test void trimsSearchAndPreservesTenantScope(){UUID tenant=UUID.randomUUID();service.memberships("  alice  ",tenant,2);verify(dao).memberships("alice",tenant,2);}
 @Test void teamDelegatesToDao(){service.team();verify(dao).team();}
}
