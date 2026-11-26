package com.restaurantos.modules.branding.service.impl;
import com.restaurantos.modules.branding.dto.BrandingSettings;
import com.restaurantos.modules.branding.repository.BrandingDao;
import com.restaurantos.modules.platform.TenantFeatureAccess;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class BrandingServiceImplTest {
 private final BrandingDao dao=mock(BrandingDao.class);
 private final TenantFeatureAccess features=mock(TenantFeatureAccess.class);
 private final BrandingServiceImpl service=new BrandingServiceImpl(dao,features);
 private final UUID tenant=UUID.randomUUID(),user=UUID.randomUUID();
 private final Authentication auth=mock(Authentication.class);
 @Test void defaultMatchesPanelPalette(){
  var s=BrandingSettings.defaults("Restaurant");
  assertEquals("#1B241F",s.primaryColor());assertEquals("#E7764C",s.accentColor());assertEquals("#F7F7F4",s.backgroundColor());
 }
 @Test void rejectsUnauthorizedTenantBeforeWrite(){
  when(auth.getPrincipal()).thenReturn(user);
  assertThrows(ResponseStatusException.class,()->service.publish(tenant,BrandingSettings.defaults("Restaurant"),auth));
  verify(dao,never()).publish(any(),any());verifyNoInteractions(features);
 }
 @Test void authorizedPublicationUsesTenantAndFeature(){
  when(auth.getPrincipal()).thenReturn(user);when(dao.canManage(tenant,user)).thenReturn(true);
  var settings=BrandingSettings.defaults("Restaurant");
  assertEquals(settings,service.publish(tenant,settings,auth));verify(features).require(tenant,"QR_MENU");verify(dao).publish(tenant,settings);
 }
 @Test void rejectsCrossTenantStoredImage(){
  when(auth.getPrincipal()).thenReturn(user);when(dao.canManage(tenant,user)).thenReturn(true);
  var settings=BrandingSettings.defaults("Restaurant").withImages("/api/v1/public/guest-website/images/"+UUID.randomUUID(),"");
  assertThrows(ResponseStatusException.class,()->service.publish(tenant,settings,auth));verify(dao,never()).publish(any(),any());
 }
 @Test void rejectsUntrustedImageScheme(){
  when(auth.getPrincipal()).thenReturn(user);when(dao.canManage(tenant,user)).thenReturn(true);
  var settings=BrandingSettings.defaults("Restaurant").withImages("javascript:alert(1)","");
  assertThrows(ResponseStatusException.class,()->service.publish(tenant,settings,auth));verify(dao,never()).publish(any(),any());
 }
}
