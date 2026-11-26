package com.restaurantos.modules.branding;
import com.restaurantos.common.api.ApiResponse;
import com.restaurantos.modules.branding.dto.BrandingSettings;
import com.restaurantos.modules.branding.service.BrandingService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.MediaType;
@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/guest-website")
public class BrandingController {
 private final BrandingService service;
 public BrandingController(BrandingService service){this.service=service;}
 @GetMapping public ApiResponse<BrandingSettings> get(@PathVariable UUID tenantId,Authentication authentication){
  return ApiResponse.success("Guest website settings",service.get(tenantId,authentication));
 }
 @PutMapping(consumes=MediaType.APPLICATION_JSON_VALUE) public ApiResponse<BrandingSettings> publish(@PathVariable UUID tenantId,@Valid @RequestBody BrandingSettings settings,Authentication authentication){
  return ApiResponse.success("Guest website published",service.publish(tenantId,settings,authentication));
 }
 @PostMapping(value="/publish",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
 public ApiResponse<BrandingSettings> publishWithImages(@PathVariable UUID tenantId,@Valid @RequestPart("settings") BrandingSettings settings,
  @RequestPart(value="logo",required=false) MultipartFile logo,@RequestPart(value="banner",required=false) MultipartFile banner,Authentication authentication){
  return ApiResponse.success("Guest website published",service.publishWithImages(tenantId,settings,logo,banner,authentication));
 }
}
