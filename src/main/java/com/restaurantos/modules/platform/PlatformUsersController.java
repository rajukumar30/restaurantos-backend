package com.restaurantos.modules.platform;
import com.restaurantos.common.api.ApiResponse;
import com.restaurantos.modules.platform.directory.dto.MembershipResponse;
import com.restaurantos.modules.platform.directory.service.PlatformDirectoryService;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;
@RestController
@RequestMapping("/api/v1/platform/users")
public class PlatformUsersController {
 private final PlatformDirectoryService service;
 public PlatformUsersController(PlatformDirectoryService service){this.service=service;}
 @GetMapping
 public ApiResponse<List<MembershipResponse>> list(@RequestParam(defaultValue="") String search,
    @RequestParam(required=false) UUID tenantId,@RequestParam(defaultValue="0") int page){
  return ApiResponse.success("Restaurant user memberships",service.memberships(search,tenantId,page));
 }
}
