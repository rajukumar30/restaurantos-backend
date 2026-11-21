package com.restaurantos.modules.tables;
import com.restaurantos.common.api.ApiResponse;
import com.restaurantos.modules.tables.dto.PublicMenuDto.PublicMenu;
import com.restaurantos.modules.tables.service.PublicQrMenuService;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1/public/qr")
public class PublicQrController {
 private final PublicQrMenuService service;
 public PublicQrController(PublicQrMenuService service){this.service=service;}
 @GetMapping("/{qrToken}")
 public ApiResponse<PublicMenu> resolve(@PathVariable String qrToken){
  return ApiResponse.success("Public menu loaded",service.resolve(qrToken));
 }
}
