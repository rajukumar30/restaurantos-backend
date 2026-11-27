package com.restaurantos.modules.branding.dto;

import jakarta.validation.constraints.*;
import java.util.Set;

public record BrandingSettings(
 @NotBlank @Size(max=120) String displayName,
 @NotNull @Size(max=160) String slogan,
 @NotNull @Size(max=2048) String logoUrl,
 @NotNull @Size(max=2048) String bannerUrl,
 @NotBlank @Pattern(regexp="CLASSIC|BOLD|MINIMAL") String theme,
 @NotBlank @Pattern(regexp="#[0-9a-fA-F]{6}") String primaryColor,
 @NotBlank @Pattern(regexp="#[0-9a-fA-F]{6}") String accentColor,
 @NotBlank @Pattern(regexp="#[0-9a-fA-F]{6}") String backgroundColor,
 @NotNull @Size(max=120) String bannerTitle,
 @NotNull @Size(max=300) String bannerText,
 @NotNull @Size(max=1000) String about,
 @NotNull @Size(max=40) String contactPhone,
 @NotNull @Size(max=300) String address,
 @NotNull @Size(max=200) String openingHours,
 @NotNull Boolean showBanner,
 @NotNull Boolean showFeatured,
 @NotNull Boolean showDetails
){
 public BrandingSettings withImages(String logo,String banner){
  return new BrandingSettings(displayName,slogan,logo,banner,theme,primaryColor,accentColor,backgroundColor,bannerTitle,bannerText,about,contactPhone,address,openingHours,showBanner,showFeatured,showDetails);
 }
 public static BrandingSettings defaults(String name){
  return legacy(name,"","","","CLASSIC");
 }
 public static BrandingSettings legacy(String name,String slogan,String logo,String banner,String theme){
  String selected=theme!=null&&Set.of("CLASSIC","BOLD","MINIMAL").contains(theme)?theme:"CLASSIC";
  String primary=switch(selected){case "BOLD"->"#7C2F20";case "MINIMAL"->"#303632";default->"#213D32";};
  String accent=switch(selected){case "BOLD"->"#E79B58";case "MINIMAL"->"#397758";default->"#B64D32";};
  String background=switch(selected){case "BOLD"->"#FFF4E6";case "MINIMAL"->"#FFFFFF";default->"#FAF7F2";};
  return new BrandingSettings(name,slogan,logo,banner,selected,primary,accent,background,"","","","","","",true,true,true);
 }
}
