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
 @GetMapping(value="/{id}",produces=MediaType.IMAGE_JPEG_VALUE)
 public ResponseEntity<byte[]> image(@PathVariable UUID id){
  return service.image(id).map(bytes->ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG)
   .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic().immutable()).body(bytes))
   .orElseGet(()->ResponseEntity.notFound().build());
 }
}
