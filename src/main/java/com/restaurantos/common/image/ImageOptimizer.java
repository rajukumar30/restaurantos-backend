package com.restaurantos.common.image;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.concurrent.Semaphore;
import javax.imageio.*;
import javax.imageio.stream.ImageOutputStream;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MultipartFile;

/** Decode only bounded raster files; create all display variants at upload time. */
public final class ImageOptimizer {
 private ImageOptimizer(){}
 private static final Semaphore PROCESSING=new Semaphore(2,true);
 public enum Kind {
  ITEM(800,640,100*1024), BANNER(960,540,120*1024), LOGO(256,256,40*1024);
  public final int width,height,maxBytes;
  Kind(int width,int height,int maxBytes){this.width=width;this.height=height;this.maxBytes=maxBytes;}
 }
 public record Image(byte[] bytes,String contentType){}
 public record MenuImages(Image detail,Image thumbnail){}
 private record Decoded(BufferedImage image,String format){}
 public static Image optimize(MultipartFile file,Kind kind){
  if(file==null)return null;
  if(kind==Kind.ITEM)return menuImages(file).detail();
  acquire();
  try{
   var decoded=decode(file,kind);
   if(kind==Kind.LOGO)return logo(decoded);
   return photo(decoded.image(),kind.width,kind.height,kind.maxBytes,80*1024);
  }catch(ResponseStatusException e){throw e;}catch(IOException|RuntimeException e){throw bad("Choose a valid cropped photo and try again.");}
  finally{PROCESSING.release();}
 }
 public static MenuImages menuImages(MultipartFile file){
  acquire();
  try{
   var source=decode(file,Kind.ITEM).image();
   return new MenuImages(photo(source,800,640,100*1024,60*1024),photo(source,400,320,40*1024,25*1024));
  }catch(ResponseStatusException e){throw e;}catch(IOException|RuntimeException e){throw bad("Choose a valid cropped photo and try again.");}
  finally{PROCESSING.release();}
 }
 private static void acquire(){
  if(!PROCESSING.tryAcquire())throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"Image processing is busy. Please retry in a moment.");
 }
 private static Decoded decode(MultipartFile file,Kind kind)throws IOException{
  if(file==null||file.isEmpty()||file.getSize()>512000)throw bad("Upload a cropped, compressed photo under 500 KB.");
  try(var input=ImageIO.createImageInputStream(new ByteArrayInputStream(file.getBytes()))){
   var readers=ImageIO.getImageReaders(input);
   if(!readers.hasNext())throw new IOException("Unsupported image");
   var reader=readers.next();
   try{
    reader.setInput(input,true,true);String format=reader.getFormatName();
    if(!("JPEG".equalsIgnoreCase(format)||"PNG".equalsIgnoreCase(format)))throw new IOException("Unsupported format");
    int w=reader.getWidth(0),h=reader.getHeight(0);
    // Accept the previous client dimensions during rolling deployment; normalize before storage.
    boolean valid=switch(kind){
     case ITEM -> (w==800&&h==640)||(w==1000&&h==800);
     case BANNER -> (w==960&&h==540)||(w==1600&&h==900);
     case LOGO -> w==h&&w>=96&&w<=512;
    };
    if(!valid)throw bad("Image has unsupported dimensions. Crop it before uploading.");
    return new Decoded(reader.read(0),format);
   }finally{reader.dispose();}
  }
 }
 private static BufferedImage scaled(BufferedImage source,int w,int h,boolean alpha){
  var image=new BufferedImage(w,h,alpha?BufferedImage.TYPE_INT_ARGB:BufferedImage.TYPE_INT_RGB);
  Graphics2D g=image.createGraphics();
  try{g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);
   if(!alpha){g.setColor(Color.WHITE);g.fillRect(0,0,w,h);}
   g.drawImage(source,0,0,w,h,null);
  }finally{g.dispose();}
  return image;
 }
 private static Image logo(Decoded decoded)throws IOException{
  BufferedImage source=decoded.image();int size=Math.min(source.getWidth(),256);
  if("JPEG".equalsIgnoreCase(decoded.format()))return photo(source,size,size,40*1024,20*1024);
  for(int n:new int[]{size,192,160,128,96}){
   if(n>size)continue;
   var out=new ByteArrayOutputStream();
   if(ImageIO.write(scaled(source,n,n,true),"PNG",out)&&out.size()<=40*1024)return new Image(out.toByteArray(),"image/png");
  }
  throw bad("Unable to optimize logo. Choose a valid image.");
 }
 private static Image photo(BufferedImage source,int w,int h,int max,int target)throws IOException{
  var rgb=scaled(source,w,h,false);byte[] best=null;
  for(float q:new float[]{.88f,.80f,.72f,.64f,.56f,.48f,.40f,.32f,.24f,.16f,.10f,.05f}){
   byte[] bytes=jpeg(rgb,q);
   if(bytes.length<=max&&best==null)best=bytes;
   if(bytes.length<=target)return new Image(bytes,"image/jpeg");
   // Do not trade obvious quality loss just to meet a soft target.
   if(best!=null&&q<=.64f)return new Image(best,"image/jpeg");
  }
  if(best!=null)return new Image(best,"image/jpeg");
  throw bad("Unable to compress photo to the maximum file size.");
 }
 private static byte[] jpeg(BufferedImage image,float quality)throws IOException{
  var writer=ImageIO.getImageWritersByFormatName("JPEG").next();
  var output=new ByteArrayOutputStream();
  try(ImageOutputStream stream=ImageIO.createImageOutputStream(output)){
   writer.setOutput(stream);var params=writer.getDefaultWriteParam();params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);params.setCompressionQuality(quality);
   writer.write(null,new IIOImage(image,null,null),params);stream.flush();return output.toByteArray();
  }finally{writer.dispose();}
 }
 private static ResponseStatusException bad(String message){return new ResponseStatusException(HttpStatus.BAD_REQUEST,message);}
}
