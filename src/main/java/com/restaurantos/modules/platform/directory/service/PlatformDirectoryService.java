package com.restaurantos.modules.platform.directory.service;
import com.restaurantos.modules.platform.directory.dto.TeamMemberResponse;
import com.restaurantos.modules.platform.directory.dto.MembershipResponse;
import java.util.List;
import java.util.UUID;
public interface PlatformDirectoryService {
 List<TeamMemberResponse> team();
 List<MembershipResponse> memberships(String search,UUID tenantId,int page);
}
