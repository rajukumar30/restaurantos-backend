package com.restaurantos.common.image;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import static org.junit.jupiter.api.Assertions.*;
class ImageOptimizerTest {
 private MockMultipartFile photo(int width,int height,boolean transparent)throws IOException{
  var image=new BufferedImage(width,height,transparent?BufferedImage.TYPE_INT_ARGB:BufferedImage.TYPE_INT_RGB);
  image.setRGB(width/2,height/2,0x80FF0000);
  var out=new ByteArrayOutputStream();String format=transparent?"png":"jpeg";ImageIO.write(image,format,out);
  return new MockMultipartFile("image","photo."+format,"image/"+format,out.toByteArray());
 }
 @Test void menuPhotoHasFixedDimensionsAndByteLimit()throws IOException{
  var variants=ImageOptimizer.menuImages(photo(1000,800,false));var img=variants.detail();
  assertTrue(variants.thumbnail().bytes().length<=40960);var thumb=ImageIO.read(new ByteArrayInputStream(variants.thumbnail().bytes()));assertEquals(400,thumb.getWidth());assertEquals(320,thumb.getHeight());
  assertTrue(img.bytes().length<=102400);assertEquals("image/jpeg",img.contentType());
  var decoded=ImageIO.read(new ByteArrayInputStream(img.bytes()));assertEquals(800,decoded.getWidth());assertEquals(640,decoded.getHeight());
 }
 @Test void bannerUsesFixedSizeAndCap()throws IOException{
  var img=ImageOptimizer.optimize(photo(1600,900,false),ImageOptimizer.Kind.BANNER);
  assertTrue(img.bytes().length<=122880);var decoded=ImageIO.read(new ByteArrayInputStream(img.bytes()));assertEquals(960,decoded.getWidth());assertEquals(540,decoded.getHeight());
 }
 @Test void logoPreservesTransparency()throws IOException{
  var img=ImageOptimizer.optimize(photo(512,512,true),ImageOptimizer.Kind.LOGO);
  assertEquals("image/png",img.contentType());assertTrue(img.bytes().length<=40960);
  assertEquals(0,ImageIO.read(new ByteArrayInputStream(img.bytes())).getRGB(0,0)>>>24);
 }
 @Test void acceptsAdaptiveSquareLogoWithoutStretching()throws IOException{
  var img=ImageOptimizer.optimize(photo(256,256,true),ImageOptimizer.Kind.LOGO);
  var decoded=ImageIO.read(new ByteArrayInputStream(img.bytes()));
  assertEquals(256,decoded.getWidth());assertEquals(256,decoded.getHeight());assertEquals(0,decoded.getRGB(0,0)>>>24);
  assertTrue(img.bytes().length<=40960);
  assertThrows(ResponseStatusException.class,()->ImageOptimizer.optimize(photo(256,192,true),ImageOptimizer.Kind.LOGO));
  assertThrows(ResponseStatusException.class,()->ImageOptimizer.optimize(photo(640,640,true),ImageOptimizer.Kind.LOGO));
 }
 @Test void complexTransparentLogoFitsNewCap()throws IOException{
  var image=new BufferedImage(256,256,BufferedImage.TYPE_INT_ARGB);var random=new java.util.Random(42);
  for(int y=0;y<256;y++)for(int x=0;x<256;x++)image.setRGB(x,y,random.nextInt());
  var out=new ByteArrayOutputStream();ImageIO.write(image,"PNG",out);
  var result=ImageOptimizer.optimize(new MockMultipartFile("image","logo.png","image/png",out.toByteArray()),ImageOptimizer.Kind.LOGO);
  assertTrue(result.bytes().length<=40960);assertEquals("image/png",result.contentType());
  var decoded=ImageIO.read(new ByteArrayInputStream(result.bytes()));assertEquals(decoded.getWidth(),decoded.getHeight());assertTrue(decoded.getColorModel().hasAlpha());
 }
 @Test void rejectsWrongDimensionsAndCorruptData()throws IOException{
  assertThrows(ResponseStatusException.class,()->ImageOptimizer.optimize(photo(200,200,false),ImageOptimizer.Kind.ITEM));
  assertThrows(ResponseStatusException.class,()->ImageOptimizer.optimize(new MockMultipartFile("image","x.jpg","image/jpeg",new byte[]{1,2,3}),ImageOptimizer.Kind.ITEM));
 }
 @Test void rejectsOversizedBytesBeforeDecode(){
  assertThrows(ResponseStatusException.class,()->ImageOptimizer.optimize(new MockMultipartFile("image","x.jpg","image/jpeg",new byte[512001]),ImageOptimizer.Kind.LOGO));
 }
}
