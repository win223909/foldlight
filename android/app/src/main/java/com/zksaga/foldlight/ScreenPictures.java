package com.zksaga.foldlight;

import android.content.Context;
import android.graphics.*;
import android.net.Uri;
import android.util.AtomicFile;
import java.io.*;
import java.nio.file.Files;

/** Independent, atomic image stores. The original shared upload is retained as a migration backup. */
final class ScreenPictures {
    private final File directory;
    ScreenPictures(Context context){directory=context.getFilesDir();}
    ScreenPictures(File directory){this.directory=directory;}
    File file(boolean cover){return new File(directory,cover?"picture-cover.png":"picture-inner.png");}
    void migrate() throws IOException {
        File legacy=new File(directory,"picture.png");
        if(!legacy.exists())return;
        for(boolean cover:new boolean[]{true,false})if(!file(cover).exists())Files.copy(legacy.toPath(),file(cover).toPath());
        Files.move(legacy.toPath(),new File(directory,"picture-legacy.png").toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }
    Bitmap load(boolean cover){
        Bitmap image=null;try(InputStream input=new AtomicFile(file(cover)).openRead()){image=BitmapFactory.decodeStream(input);}catch(IOException ignored){}
        return image==null?DemoImage.create(cover):fit(image,cover);
    }
    Bitmap importImage(Context context,Uri uri,boolean cover) throws IOException {
        Bitmap decoded=ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.getContentResolver(),uri),(decoder,info,source)->{
            int w=info.getSize().getWidth(),h=info.getSize().getHeight();float scale=Math.min(1,4096f/Math.max(w,h));
            decoder.setTargetSize(Math.max(1,Math.round(w*scale)),Math.max(1,Math.round(h*scale)));decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
        });
        Bitmap fitted=fit(decoded,cover);
        AtomicFile destination=new AtomicFile(file(cover));FileOutputStream output=null;
        try{output=destination.startWrite();if(!fitted.compress(Bitmap.CompressFormat.PNG,100,output))throw new IOException("Image encoding failed");destination.finishWrite(output);}
        catch(IOException e){destination.failWrite(output);throw e;}
        return fitted;
    }
    Bitmap reset(boolean cover) throws IOException {File f=file(cover);if(f.exists()&&!f.delete())throw new IOException("Unable to reset image");return DemoImage.create(cover);}
    static Bitmap fit(Bitmap source,boolean cover){
        FoldDeviceProfile profile=FoldDeviceProfile.forModel(android.os.Build.MODEL);
        int width=profile==null?(cover?1248:2448):profile.width(cover),height=profile==null?(cover?1972:1848):profile.height(cover);
        if(source.getWidth()==width&&source.getHeight()==height)return source;
        Bitmap result=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(result);canvas.drawColor(0xff101b2b);
        float scale=Math.max((float)width/source.getWidth(),(float)height/source.getHeight());
        float w=source.getWidth()*scale,h=source.getHeight()*scale;
        canvas.drawBitmap(source,null,new RectF((width-w)/2,(height-h)/2,(width+w)/2,(height+h)/2),new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG));return result;
    }
}
