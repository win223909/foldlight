package com.zksaga.foldlight;

import android.content.Context;
import android.graphics.*;
import android.net.Uri;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.*;
import java.nio.file.Files;
import static org.junit.Assert.*;

/** Device decoder + atomic storage tests, isolated from the user's actual pictures. */
@RunWith(AndroidJUnit4.class)
public class ScreenPicturesTest {
    private Context context;private File dir;private ScreenPictures store;
    @Before public void setup() throws Exception {context=InstrumentationRegistry.getInstrumentation().getTargetContext();dir=Files.createTempDirectory(context.getCacheDir().toPath(),"screen-images-").toFile();store=new ScreenPictures(dir);}
    private File image(String name,int color) throws Exception {Bitmap b=Bitmap.createBitmap(120,80,Bitmap.Config.ARGB_8888);b.eraseColor(color);File f=new File(dir,name);try(FileOutputStream out=new FileOutputStream(f)){assertTrue(b.compress(Bitmap.CompressFormat.PNG,100,out));}b.recycle();return f;}
    @Test public void independentImportsFitEachScreenAndSurviveReload() throws Exception {
        store.importImage(context,Uri.fromFile(image("blue.png",Color.BLUE)),true);
        byte[] coverBefore=Files.readAllBytes(store.file(true).toPath());
        store.importImage(context,Uri.fromFile(image("red.png",Color.RED)),false);
        assertArrayEquals(coverBefore,Files.readAllBytes(store.file(true).toPath()));
        ScreenPictures reloaded=new ScreenPictures(dir);Bitmap cover=reloaded.load(true),inner=reloaded.load(false);
        assertEquals(1248,cover.getWidth());assertEquals(1972,cover.getHeight());assertEquals(Color.BLUE,cover.getPixel(300,300));
        assertEquals(2448,inner.getWidth());assertEquals(1848,inner.getHeight());assertEquals(Color.RED,inner.getPixel(300,300));
    }
    @Test public void unreadableImportDoesNotReplaceSavedPicture() throws Exception {
        store.importImage(context,Uri.fromFile(image("saved.png",Color.GREEN)),true);byte[] before=Files.readAllBytes(store.file(true).toPath());
        try{store.importImage(context,Uri.fromFile(new File(dir,"missing.png")),true);fail("Expected decode failure");}catch(IOException expected){}
        assertArrayEquals(before,Files.readAllBytes(store.file(true).toPath()));
    }
    @Test public void migrationAndResetAreIndependent() throws Exception {
        image("picture.png",Color.MAGENTA);store.migrate();assertFalse(new File(dir,"picture.png").exists());assertTrue(new File(dir,"picture-legacy.png").exists());
        byte[] inner=Files.readAllBytes(store.file(false).toPath());store.reset(true);store.migrate();
        assertFalse(store.file(true).exists());assertArrayEquals(inner,Files.readAllBytes(store.file(false).toPath()));
    }
    @After public void cleanup() throws Exception {try(java.util.stream.Stream<java.nio.file.Path> paths=Files.walk(dir.toPath())){paths.sorted(java.util.Comparator.reverseOrder()).forEach(p->{try{Files.delete(p);}catch(IOException ignored){}});}}
}
