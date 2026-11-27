package com.restaurantos.modules.platform.directory.service.impl;
import com.restaurantos.modules.platform.directory.dto.TeamMemberResponse;
import com.restaurantos.modules.platform.directory.dto.MembershipResponse;
import com.restaurantos.modules.platform.directory.repository.PlatformDirectoryDao;
import com.restaurantos.modules.platform.directory.service.PlatformDirectoryService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.UUID;
@Service
public class PlatformDirectoryServiceImpl implements PlatformDirectoryService {
 private final PlatformDirectoryDao dao;
 public PlatformDirectoryServiceImpl(PlatformDirectoryDao dao){this.dao=dao;}
 @Transactional(readOnly=true)
 public List<TeamMemberResponse> team(){return dao.team();}
 @Transactional(readOnly=true)
 public List<MembershipResponse> memberships(String search,UUID tenantId,int page){
  String term=search==null?"":search.strip();
  if(term.length()>100||page<0||page>100000)
   throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid search or page");
  return dao.memberships(term,tenantId,page);
 }
}
