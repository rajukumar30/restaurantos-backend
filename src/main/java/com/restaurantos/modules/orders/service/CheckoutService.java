package com.restaurantos.modules.orders.service;
import com.restaurantos.modules.orders.dto.CheckoutDto.*;
import java.util.UUID;
import org.springframework.security.core.Authentication;
public interface CheckoutService{
 Detail guest(String qr,UUID order,boolean begin);
 Detail guestVisit(Scope scope,boolean begin);
 Page page(UUID tenant,UUID branch,String filter,String search,int page,int size,Authentication auth);
 Detail detail(UUID tenant,UUID branch,UUID visit,Authentication auth);
 Detail change(UUID tenant,UUID branch,UUID visit,boolean begin,Authentication auth);
 Detail confirm(UUID tenant,UUID visit,Confirm request,Authentication auth);
 Detail report(Scope scope,UUID session,Report report);
 Detail resolve(UUID tenant,UUID visit,UUID dispute,Resolve request,Authentication auth);
 Merchant merchant(UUID tenant,UUID branch,Merchant update,Authentication auth);
}
