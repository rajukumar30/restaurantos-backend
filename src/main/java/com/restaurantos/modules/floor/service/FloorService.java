package com.restaurantos.modules.floor.service;
import com.restaurantos.modules.floor.dto.FloorDto.*;
import java.util.UUID;
import org.springframework.security.core.Authentication;
public interface FloorService {
 FloorSnapshot snapshot(UUID tenant,UUID branch,Authentication auth);
 FloorDetail detail(UUID tenant,UUID branch,UUID table,Authentication auth);
 TableState action(UUID tenant,UUID table,FloorAction request,Authentication auth);
}
