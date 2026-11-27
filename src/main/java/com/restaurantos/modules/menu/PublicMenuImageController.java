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
  return response(id,false);
 }
 @GetMapping(value="/{id}/thumbnail",produces=MediaType.IMAGE_JPEG_VALUE)
 public ResponseEntity<byte[]> thumbnail(@PathVariable UUID id){return response(id,true);}
 private ResponseEntity<byte[]> response(UUID id,boolean thumbnail){
  return service.publicImage(id,thumbnail).map(bytes->ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG)
   .cacheControl(CacheControl.maxAge(Duration.ofHours(24)).cachePublic().immutable()).body(bytes))
   .orElseGet(()->ResponseEntity.notFound().build());
 }
}
