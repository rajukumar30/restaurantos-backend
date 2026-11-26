package com.restaurantos.modules.menu;
import com.restaurantos.modules.menu.service.MenuService;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.time.Duration;
import java.util.UUID;
@RestController
@RequestMapping("/api/v1/public/menu/images")
public class PublicMenuImageController {
 private final MenuService service;
 public PublicMenuImageController(MenuService service){this.service=service;}
 @GetMapping(value="/{id}",produces=MediaType.IMAGE_JPEG_VALUE)
 public ResponseEntity<byte[]> image(@PathVariable UUID id){
  return service.publicImage(id).map(bytes->ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG)
   .cacheControl(CacheControl.maxAge(Duration.ofHours(24)).cachePublic().immutable()).body(bytes))
   .orElseGet(()->ResponseEntity.notFound().build());
 }
}
