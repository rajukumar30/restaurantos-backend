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
  var img=ImageOptimizer.optimize(photo(1000,800,false),ImageOptimizer.Kind.ITEM);
  assertTrue(img.bytes().length<=184320);assertEquals("image/jpeg",img.contentType());
  var decoded=ImageIO.read(new ByteArrayInputStream(img.bytes()));assertEquals(1000,decoded.getWidth());assertEquals(800,decoded.getHeight());
 }
 @Test void bannerUsesFixedSizeAndCap()throws IOException{
  var img=ImageOptimizer.optimize(photo(1600,900,false),ImageOptimizer.Kind.BANNER);
  assertTrue(img.bytes().length<=256000);var decoded=ImageIO.read(new ByteArrayInputStream(img.bytes()));assertEquals(1600,decoded.getWidth());assertEquals(900,decoded.getHeight());
 }
 @Test void logoPreservesTransparency()throws IOException{
  var img=ImageOptimizer.optimize(photo(512,512,true),ImageOptimizer.Kind.LOGO);
  assertEquals("image/png",img.contentType());assertTrue(img.bytes().length<=122880);
  assertEquals(0,ImageIO.read(new ByteArrayInputStream(img.bytes())).getRGB(0,0)>>>24);
 }
 @Test void acceptsAdaptiveSquareLogoWithoutStretching()throws IOException{
  var img=ImageOptimizer.optimize(photo(256,256,true),ImageOptimizer.Kind.LOGO);
  var decoded=ImageIO.read(new ByteArrayInputStream(img.bytes()));
  assertEquals(256,decoded.getWidth());assertEquals(256,decoded.getHeight());assertEquals(0,decoded.getRGB(0,0)>>>24);
  assertTrue(img.bytes().length<=122880);
  assertThrows(ResponseStatusException.class,()->ImageOptimizer.optimize(photo(256,192,true),ImageOptimizer.Kind.LOGO));
  assertThrows(ResponseStatusException.class,()->ImageOptimizer.optimize(photo(640,640,true),ImageOptimizer.Kind.LOGO));
 }
 @Test void rejectsWrongDimensionsAndCorruptData()throws IOException{
  assertThrows(ResponseStatusException.class,()->ImageOptimizer.optimize(photo(200,200,false),ImageOptimizer.Kind.ITEM));
  assertThrows(ResponseStatusException.class,()->ImageOptimizer.optimize(new MockMultipartFile("image","x.jpg","image/jpeg",new byte[]{1,2,3}),ImageOptimizer.Kind.ITEM));
 }
 @Test void rejectsOversizedBytesBeforeDecode(){
  assertThrows(ResponseStatusException.class,()->ImageOptimizer.optimize(new MockMultipartFile("image","x.jpg","image/jpeg",new byte[512001]),ImageOptimizer.Kind.LOGO));
 }
}
