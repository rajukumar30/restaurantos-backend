package com.restaurantos.common.image;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import javax.imageio.*;
import javax.imageio.stream.ImageOutputStream;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MultipartFile;

/** Only fixed-size, bounded, decoded raster images can be persisted. */
public final class ImageOptimizer {
 private ImageOptimizer(){}
 public enum Kind {
  ITEM(1000,800,180*1024), BANNER(1600,900,250*1024), LOGO(512,512,120*1024);
  public final int width,height,maxBytes;
  Kind(int width,int height,int maxBytes){this.width=width;this.height=height;this.maxBytes=maxBytes;}
 }
 public record Image(byte[] bytes,String contentType){}
 public static Image optimize(MultipartFile file,Kind kind){
  if(file==null)return null;
  if(file.isEmpty()||file.getSize()>512000)throw bad("Upload a cropped, compressed photo under 500 KB.");
  try(var input=ImageIO.createImageInputStream(new ByteArrayInputStream(file.getBytes()))){
   var readers=ImageIO.getImageReaders(input);
   if(!readers.hasNext())throw new IOException("Unsupported image");
   var reader=readers.next();
   BufferedImage image;
   String format;
   try{
    reader.setInput(input,true,true);format=reader.getFormatName();
    if(!("JPEG".equalsIgnoreCase(format)||"PNG".equalsIgnoreCase(format)))throw new IOException("Unsupported format");
    int w=reader.getWidth(0),h=reader.getHeight(0);
    if(w!=kind.width||h!=kind.height)throw bad("Image must be "+kind.width+" × "+kind.height+" pixels. Crop it before uploading.");
    image=reader.read(0);
   }finally{reader.dispose();}
   if(kind==Kind.LOGO){
    // Re-encode PNG to preserve alpha and remove metadata. JPEG logos remain JPEG.
    if("PNG".equalsIgnoreCase(format)){
     var out=new ByteArrayOutputStream();
     if(ImageIO.write(image,"PNG",out)&&out.size()<=kind.maxBytes)return new Image(out.toByteArray(),"image/png");
     throw bad("Logo exceeds 120 KB. Choose a simpler logo or compress it further.");
    }
   }
   BufferedImage rgb=new BufferedImage(kind.width,kind.height,BufferedImage.TYPE_INT_RGB);
   Graphics2D graphics=rgb.createGraphics();try{graphics.setColor(Color.WHITE);graphics.fillRect(0,0,kind.width,kind.height);graphics.drawImage(image,0,0,null);}finally{graphics.dispose();}
   for(float quality:new float[]{.83f,.72f,.60f,.48f,.36f,.25f}){
    byte[] bytes=jpeg(rgb,quality);
    if(bytes.length<=kind.maxBytes)return new Image(bytes,"image/jpeg");
   }
   throw bad("Image cannot be compressed to the required size. Choose a simpler photo.");
  }catch(ResponseStatusException e){throw e;}catch(IOException|RuntimeException e){throw bad("Choose a valid cropped photo and try again.");}
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
