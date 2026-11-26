package com.restaurantos.modules.branding;
import com.restaurantos.modules.branding.service.BrandingService;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.time.Duration;
import java.util.UUID;
@RestController
@RequestMapping("/api/v1/public/guest-website/images")
public class PublicBrandImageController {
 private final BrandingService service;
 public PublicBrandImageController(BrandingService service){this.service=service;}
 @GetMapping("/{id}")
 public ResponseEntity<byte[]> image(@PathVariable UUID id){
  return service.image(id).map(bytes->ResponseEntity.ok().contentType(bytes.length>=8&&(bytes[0]&255)==137&&bytes[1]==80&&bytes[2]==78&&bytes[3]==71?MediaType.IMAGE_PNG:MediaType.IMAGE_JPEG)
   .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic().immutable()).body(bytes))
   .orElseGet(()->ResponseEntity.notFound().build());
 }
}
