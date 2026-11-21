package com.restaurantos.modules.platform;
import com.restaurantos.common.api.ApiResponse;
import com.restaurantos.modules.platform.directory.dto.TeamMemberResponse;
import com.restaurantos.modules.platform.directory.service.PlatformDirectoryService;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController
@RequestMapping("/api/v1/platform/team")
public class PlatformTeamController {
 private final PlatformDirectoryService service;
 public PlatformTeamController(PlatformDirectoryService service){this.service=service;}
 @GetMapping
 public ApiResponse<List<TeamMemberResponse>> list(){
  return ApiResponse.success("Platform administrators",service.team());
 }
}
