package com.zksaga.foldlight;

import android.graphics.*;
import android.opengl.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.*;
import org.junit.runner.RunWith;
import java.nio.ByteBuffer;
import static org.junit.Assert.*;

/** Real device GPU pixels: silhouette contraction, stationary right panel, reversed cover hinge. */
@RunWith(AndroidJUnit4.class)
public class ProjectedPanelsTest {
    private EGLDisplay display;private EGLContext context;private EGLSurface surface;private EGLConfig activeConfig;
    private FoldRenderer renderer;private static final int W=256,H=192;
    @Before public void setup(){
        display=EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);assertTrue(EGL14.eglInitialize(display,new int[2],0,new int[2],0));
        EGLConfig[] config=new EGLConfig[1];int[] count=new int[1];
        assertTrue(EGL14.eglChooseConfig(display,new int[]{EGL14.EGL_SURFACE_TYPE,EGL14.EGL_PBUFFER_BIT,EGL14.EGL_RENDERABLE_TYPE,0x40,EGL14.EGL_RED_SIZE,8,EGL14.EGL_GREEN_SIZE,8,EGL14.EGL_BLUE_SIZE,8,EGL14.EGL_ALPHA_SIZE,8,EGL14.EGL_NONE},0,config,0,1,count,0));assertTrue(count[0]>0);
        activeConfig=config[0];context=EGL14.eglCreateContext(display,config[0],EGL14.EGL_NO_CONTEXT,new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION,3,EGL14.EGL_NONE},0);
        surface=EGL14.eglCreatePbufferSurface(display,config[0],new int[]{EGL14.EGL_WIDTH,W,EGL14.EGL_HEIGHT,W,EGL14.EGL_NONE},0);
        assertTrue(EGL14.eglMakeCurrent(display,surface,surface,context));
        Bitmap bitmap=Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888);bitmap.eraseColor(Color.rgb(60,160,220));
        renderer=new FoldRenderer(bitmap);renderer.onSurfaceCreated(null,null);renderer.onSurfaceChanged(null,W,H);
    }
    @Test public void fullFrostAndGradientPreserveStationaryHalf(){
        renderer.hinge=110;renderer.visualTilt=40;renderer.innerRevealEnabled=false;renderer.blur=0;
        byte[] sharp=draw();renderer.blur=1;renderer.frostGradient=1;byte[] directional=draw();
        renderer.frostGradient=0;byte[] uniform=draw();
        int changed=0;
        for(int y=0;y<H;y++)for(int x=0;x<W;x++)for(int c=0;c<4;c++){
            int i=(y*W+x)*4+c;
            if(x>=W/2){assertEquals(sharp[i],directional[i]);assertEquals(sharp[i],uniform[i]);}
            else if(directional[i]!=uniform[i])changed++;
        }
        assertTrue("gradient slider changes the moving glass and feather",changed>50);
        renderer.coverMaxAngle=100;renderer.moveRight=true;renderer.split=0;renderer.visualTilt=25;
        draw();renderer.frostGradient=1;draw();
    }
    private byte[] draw(){return draw(W,H);}
    private byte[] draw(int w,int h){renderer.onSurfaceChanged(null,w,h);renderer.onDrawFrame(null);assertEquals("",renderer.error);ByteBuffer data=ByteBuffer.allocateDirect(w*h*4);GLES30.glReadPixels(0,0,w,h,GLES30.GL_RGBA,GLES30.GL_UNSIGNED_BYTE,data);byte[] bytes=new byte[w*h*4];data.get(bytes);return bytes;}
    private int channel(byte[] data,int x,int y,int c){return data[(y*W+x)*4+c]&255;}
    @Test public void coverDimmingIncludesBackgroundAndDoesNotDimInnerPanel(){
        renderer.coverMaxAngle=0;renderer.split=0;renderer.moveRight=true;
        renderer.coverBrightness=1;byte[] normal=draw();renderer.coverBrightness=.5f;byte[] half=draw();
        assertTrue("whole glass surface dims",channel(half,10,H/2,2)<channel(normal,10,H/2,2));
        assertTrue("far edge has deeper haze",channel(half,10,H/2,2)>channel(half,W-10,H/2,2));
        assertTrue("mid-transition has no black cutout",channel(half,W-10,H/2,2)>15);
        for(int y=10;y<H-10;y++)for(int x=10;x<W-10;x+=10)assertEquals("no irregular vertical boundary",channel(half,x,H/2,2),channel(half,x,y,2),1);
        int previous=255;
        for(float level:new float[]{1,.9f,.75f,.5f,.25f,0}){
            renderer.coverBrightness=level;int value=channel(draw(),W/2,H/2,2);assertTrue(value<=previous);previous=value;
        }
        assertEquals(0,previous);
        renderer.coverMaxAngle=180;renderer.hinge=FoldMath.coverHinge(160);renderer.coverBrightness=0;
        byte[] black=draw();for(int i=0;i<black.length;i++)assertEquals(i%4==3?255:0,black[i]&255);
        renderer.coverMaxAngle=-1;renderer.split=.5f;renderer.moveRight=false;renderer.hinge=110;
        byte[] inner=draw();renderer.coverBrightness=1;assertArrayEquals("inner brightness must be unaffected",inner,draw());
        renderer.coverMaxAngle=0;renderer.split=0;renderer.moveRight=true;assertArrayEquals("disabling dim restores original pixels",normal,draw());
    }
    @Test public void innerClearsThroughGlassWithoutRevealBoundaryAndRightStaysSharp(){
        renderer.innerRevealEnabled=true;renderer.splitEnabled=false;renderer.hinge=180;byte[] full=draw();
        renderer.hinge=90;byte[] dark=draw();assertEquals(0,channel(dark,W/2-10,H/2,2));
        renderer.hinge=135;byte[] middle=draw();
        assertTrue("far left has haze but no black cutout",channel(middle,10,H/2,2)>15);
        assertTrue("glass clears first near the hinge",channel(middle,W/2-10,H/2,2)>channel(middle,10,H/2,2));
        for(int y=10;y<H-10;y++)for(int x=10;x<W/2-10;x+=10)assertEquals("no irregular vertical boundary",channel(middle,x,H/2,2),channel(middle,x,y,2),1);
        for(int y=3;y<H-3;y++)for(int x=W/2+2;x<W-2;x++)for(int c=0;c<4;c++)assertEquals(channel(full,x,y,c),channel(middle,x,y,c));
        int near=0,far=0;
        for(int angle=90;angle<=180;angle++){
            renderer.hinge=angle;byte[] pixels=draw();int nextNear=channel(pixels,W/2-10,H/2,2),nextFar=channel(pixels,10,H/2,2);
            assertTrue("near hinge clears continuously",nextNear>=near&&nextNear-near<=8);
            assertTrue("far edge clears continuously",nextFar>=far&&nextFar-far<=8);
            near=nextNear;far=nextFar;
        }
        renderer.hinge=180;assertArrayEquals(full,draw());
    }
    @Test public void innerBrightnessOnlyAffectsLeftHalfAndNeverCover(){
        renderer.innerRevealEnabled=true;renderer.splitEnabled=false;renderer.hinge=135;
        renderer.innerBrightness=1;byte[] clear=draw();renderer.innerBrightness=.5f;byte[] dim=draw();renderer.innerBrightness=0;byte[] dark=draw();
        assertTrue(channel(dim,20,H/2,2)>0&&channel(dim,20,H/2,2)<channel(clear,20,H/2,2));
        assertEquals(0,channel(dark,20,H/2,2));
        for(int y=4;y<H-4;y++)for(int x=W/2+3;x<W-3;x++)for(int c=0;c<4;c++){
            assertEquals(channel(clear,x,y,c),channel(dim,x,y,c));assertEquals(channel(clear,x,y,c),channel(dark,x,y,c));
        }
        renderer.coverMaxAngle=0;renderer.moveRight=true;renderer.split=0;renderer.coverBrightness=.5f;
        byte[] cover=draw();renderer.innerBrightness=1;assertArrayEquals(cover,draw());
    }
    @Test public void cachedGaussianHasSmoothWideSpreadAndStaysStable(){
        Bitmap image=Bitmap.createBitmap(512,384,Bitmap.Config.ARGB_8888);image.eraseColor(Color.BLACK);
        for(int y=0;y<384;y++)for(int x=160;x<176;x++)image.setPixel(x,y,Color.WHITE);
        renderer.setImage(image);renderer.split=0;renderer.moveRight=true;renderer.coverMaxAngle=50;renderer.hinge=0;renderer.blur=.2f;
        byte[] blurred=draw();assertArrayEquals("no random temporal noise",blurred,draw());
        renderer.blur=0;byte[] sharp=draw();int softJump=0,sharpJump=0;
        for(int x=8;x<80;x++){softJump=Math.max(softJump,Math.abs(channel(blurred,x,H/2,0)-channel(blurred,x+1,H/2,0)));sharpJump=Math.max(sharpJump,Math.abs(channel(sharp,x,H/2,0)-channel(sharp,x+1,H/2,0)));}
        assertTrue("blur must distribute the edge instead of leaving a hard block: "+softJump+" / "+sharpJump,softJump<sharpJump*.4);
    }
    @Test public void fullResolutionDualRenderTiming() throws Exception {
        EGLConfig[] cfg=new EGLConfig[1];int[] count=new int[1];
        assertTrue(EGL14.eglChooseConfig(display,new int[]{EGL14.EGL_SURFACE_TYPE,EGL14.EGL_PBUFFER_BIT,EGL14.EGL_RENDERABLE_TYPE,0x40,EGL14.EGL_RED_SIZE,8,EGL14.EGL_GREEN_SIZE,8,EGL14.EGL_BLUE_SIZE,8,EGL14.EGL_NONE},0,cfg,0,1,count,0));
        EGLSurface large=EGL14.eglCreatePbufferSurface(display,activeConfig,new int[]{EGL14.EGL_WIDTH,2448,EGL14.EGL_HEIGHT,1972,EGL14.EGL_NONE},0);
        assertTrue(EGL14.eglMakeCurrent(display,large,large,context));
        try{
            android.content.Context app=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().getTargetContext();
            ScreenPictures pictures=new ScreenPictures(app);
            FoldRenderer inner=new FoldRenderer(pictures.load(false)),cover=new FoldRenderer(pictures.load(true));
            inner.innerRevealEnabled=true;inner.onSurfaceCreated(null,null);inner.onSurfaceChanged(null,2448,1848);inner.hinge=120;inner.onDrawFrame(null);
            cover.coverMaxAngle=100;cover.split=0;cover.moveRight=true;cover.onSurfaceCreated(null,null);cover.onSurfaceChanged(null,1248,1972);cover.onDrawFrame(null);GLES30.glFinish();
            double total=0,max=0;
            for(int i=0;i<40;i++){
                long start=System.nanoTime();inner.onSurfaceChanged(null,2448,1848);inner.hinge=100+i;inner.onDrawFrame(null);
                cover.onSurfaceChanged(null,1248,1972);cover.hinge=150-i;cover.coverBrightness=.8f;cover.onDrawFrame(null);GLES30.glFinish();
                double ms=(System.nanoTime()-start)/1e6;if(i>=5){total+=ms;max=Math.max(max,ms);}
            }
            assertEquals("",inner.error);assertEquals("",cover.error);
            android.util.Log.i("FoldlightBenchmark","dual_full_resolution_avg_ms="+total/35+" max_ms="+max);
            cover.onSurfaceChanged(null,1248,1972);cover.hinge=120;cover.coverBrightness=.6f;cover.blur=.13f;cover.onDrawFrame(null);saveGpu(app,"glass-cover.png",1248,1972);
            inner.onSurfaceChanged(null,2448,1848);inner.hinge=140;inner.blur=.13f;inner.onDrawFrame(null);saveGpu(app,"glass-inner.png",2448,1848);
        }finally{EGL14.eglMakeCurrent(display,surface,surface,context);EGL14.eglDestroySurface(display,large);}
    }
    private void saveGpu(android.content.Context app,String name,int w,int h)throws Exception{
        ByteBuffer bytes=ByteBuffer.allocateDirect(w*h*4);GLES30.glReadPixels(0,0,w,h,GLES30.GL_RGBA,GLES30.GL_UNSIGNED_BYTE,bytes);
        int[] pixels=new int[w*h];for(int y=0;y<h;y++)for(int x=0;x<w;x++){int r=bytes.get()&255,g=bytes.get()&255,b=bytes.get()&255,a=bytes.get()&255;pixels[(h-1-y)*w+x]=Color.argb(a,r,g,b);}
        Bitmap result=Bitmap.createBitmap(pixels,w,h,Bitmap.Config.ARGB_8888);
        try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(app.getCacheDir(),name))){result.compress(Bitmap.CompressFormat.PNG,100,out);}
    }
    @Test public void innerLeftShrinksWhileRightPixelsStayFixed(){
        renderer.hinge=180;byte[] flat=draw();renderer.hinge=90;byte[] folded=draw();
        assertTrue(channel(flat,4,H/2,2)>150);assertTrue(channel(folded,4,H/2,2)<15);
        assertTrue(channel(folded,W/2-4,H/2,2)>100);
        for(int y=4;y<H-4;y++)for(int x=W/2+3;x<W-3;x++)for(int c=0;c<4;c++)assertEquals(channel(flat,x,y,c),channel(folded,x,y,c));
    }
    @Test public void coverContractsTowardItsLeftEdge(){
        renderer.split=0;renderer.moveRight=true;renderer.coverMaxAngle=90;renderer.hinge=FoldMath.coverHinge(60);byte[] image=draw();
        assertTrue("left edge should stay visible",channel(image,3,H/2,2)>100);
        assertTrue("right edge should withdraw",channel(image,W-4,H/2,2)<15);
    }
    @Test public void coverMaximumControlsProjectionAndCrossesNinetyWithoutWrapping(){
        renderer.split=0;renderer.moveRight=true;renderer.blur=0;renderer.coverMaxAngle=0;
        renderer.hinge=FoldMath.coverHinge(0);byte[] flat=draw();
        renderer.hinge=FoldMath.coverHinge(180);assertArrayEquals(flat,draw());
        renderer.coverMaxAngle=FoldMath.coverTilt(90,120);byte[] small=draw();
        renderer.coverMaxAngle=120;renderer.hinge=FoldMath.coverHinge(90);
        assertArrayEquals("physical angle uses the same early-response projection",small,draw());
        renderer.coverMaxAngle=90;renderer.hinge=FoldMath.coverHinge(100);byte[] before=draw();
        renderer.hinge=FoldMath.coverHinge(140);byte[] after=draw();
        assertFalse("cover still changes after the former 60 degree cutoff",java.util.Arrays.equals(before,after));
        renderer.coverMaxAngle=180;renderer.blur=.2f;
        int previous=W*H;
        for(float angle:new float[]{85,88,89,89.5f,89.9f,90,90.1f,100,120,150,180}){
            renderer.hinge=FoldMath.coverHinge(angle);byte[] pixels=draw();int visible=0;
            for(int y=0;y<H;y++)for(int x=0;x<W;x++)if(channel(pixels,x,y,2)>15)visible++;
            assertTrue("no flash approaching the edge: "+angle,visible<=previous);previous=visible;
            if(angle>=90)assertEquals("back-facing plane is outside the left viewport",0,visible);
        }
        renderer.hinge=FoldMath.coverHinge(0);assertArrayEquals("closing returns to the original sharp picture",flat,draw());
    }
    @Test public void defaultFrostReducesMovingDetailButKeepsRightAndFlatImageSharp(){
        Bitmap checker=Bitmap.createBitmap(W,H,Bitmap.Config.ARGB_8888);
        for(int y=0;y<H;y++)for(int x=0;x<W;x++)checker.setPixel(x,y,((x/4+y/4)%2==0)?Color.WHITE:Color.BLACK);
        renderer.setImage(checker);renderer.hinge=120;renderer.blur=0;byte[] sharp=draw();
        renderer.blur=.08f;byte[] frosted=draw();
        double sharpEnergy=0,frostEnergy=0;
        for(int y=40;y<150;y++)for(int x=60;x<85;x++){
            sharpEnergy+=Math.abs(channel(sharp,x,y,0)-channel(sharp,x+1,y,0));
            frostEnergy+=Math.abs(channel(frosted,x,y,0)-channel(frosted,x+1,y,0));
        }
        assertTrue("default frost must visibly soften the far moving region: "+frostEnergy+" / "+sharpEnergy,frostEnergy<sharpEnergy*.65);
        for(int y=5;y<H-5;y++)for(int x=W/2+3;x<W-3;x++)for(int c=0;c<4;c++)assertEquals(channel(sharp,x,y,c),channel(frosted,x,y,c));
        renderer.hinge=180;byte[] flatFrost=draw();renderer.blur=0;assertArrayEquals(flatFrost,draw());
    }
    @Test public void allDisplayRotationsPreserveTheSamePhysicalImageAndHinge(){
        Bitmap pattern=Bitmap.createBitmap(W,H,Bitmap.Config.ARGB_8888);
        for(int y=0;y<H;y++)for(int x=0;x<W;x++)pattern.setPixel(x,y,Color.rgb(x,y,(x+y)/2));
        renderer.setImage(pattern);renderer.hinge=115;byte[] natural=draw();
        for(int rotation=1;rotation<=3;rotation++){
            renderer.displayRotation=rotation;int w=rotation%2==0?W:H,h=rotation%2==0?H:W;
            byte[] rotated=draw(w,h);
            for(int y=8;y<H-8;y+=3)for(int x=8;x<W-8;x+=3){
                int rx=rotation==1?y:rotation==2?W-1-x:H-1-y;
                int ry=rotation==1?W-1-x:rotation==2?H-1-y:x;
                for(int c=0;c<3;c++)assertEquals("physical pixel "+x+","+y+" rotation "+rotation,
                    natural[((H-1-y)*W+x)*4+c]&255,rotated[((h-1-ry)*w+rx)*4+c]&255,2);
            }
        }
    }
    private double detail(byte[] pixels,int left,int right){
        double energy=0;for(int x=left;x<right;x++)for(int y=60;y<130;y++)energy+=Math.abs(channel(pixels,x,y+1,0)-channel(pixels,x,y,0));return energy;
    }
    @Test public void frostBuildsAwayFromEachPanelsHinge(){
        Bitmap stripes=Bitmap.createBitmap(W,H,Bitmap.Config.ARGB_8888);
        for(int y=0;y<H;y++)for(int x=0;x<W;x++)stripes.setPixel(x,y,y/4%2==0?Color.WHITE:Color.BLACK);
        renderer.setImage(stripes);renderer.hinge=120;
        for(boolean cover:new boolean[]{false,true}){
            renderer.moveRight=cover;renderer.split=cover?0:.5f;renderer.strength=1.5f;
            renderer.blur=0;byte[] sharp=draw();renderer.blur=.08f;byte[] frost=draw();
            int near=cover?12:112,far=cover?110:62;
            double nearRatio=detail(frost,near,near+8)/detail(sharp,near,near+8);
            double farRatio=detail(frost,far,far+8)/detail(sharp,far,far+8);
            assertTrue("clear near hinge "+cover+" "+nearRatio,nearRatio>.8);
            assertTrue("frost increases away from hinge "+cover+" "+nearRatio+" -> "+farRatio,farRatio<nearRatio*.6);
        }
    }
    private int transitionWidth(byte[] pixels,int x){
        int peak=channel(pixels,x,H/2,0),count=0;
        for(int y=0;y<H/2;y++){int value=channel(pixels,x,y,0);if(value>12&&value<peak*.9)count++;}return count;
    }
    @Test public void silhouetteFeathersWithTheSameDirectionalGradient(){
        Bitmap white=Bitmap.createBitmap(W,H,Bitmap.Config.ARGB_8888);white.eraseColor(Color.WHITE);renderer.setImage(white);renderer.hinge=120;
        for(boolean cover:new boolean[]{false,true}){
            renderer.moveRight=cover;renderer.split=cover?0:.5f;renderer.strength=1.5f;
            renderer.blur=0;byte[] sharp=draw();renderer.blur=.08f;byte[] frost=draw();
            int near=cover?16:116,far=cover?114:66;
            int nearWidth=transitionWidth(frost,near),farWidth=transitionWidth(frost,far);
            assertTrue("edge blur grows away from hinge "+cover+" "+nearWidth+" -> "+farWidth,farWidth>=nearWidth+3);
            boolean halo=false;for(int y=0;y<H/2;y++)if(channel(sharp,far,y,0)<10&&channel(frost,far,y,0)>20)halo=true;
            assertTrue("feather extends beyond the former hard silhouette "+cover,halo);
        }
    }
    @Test public void referenceGlassPreservesRightHalfAndReturnsToOriginalMode(){
        renderer.referenceGlass=true;renderer.innerRevealEnabled=true;renderer.innerBrightness=1;
        renderer.hinge=180;renderer.visualTilt=0;byte[] flat=draw();
        for(float angle:new float[]{10,45,90,130,170}){
            renderer.hinge=angle;renderer.visualTilt=FoldMath.panelTilt(angle,.9f);
            renderer.innerBrightness=angle/180;renderer.blur=.6f;byte[] folded=draw();
            for(int y=4;y<H-4;y++)for(int x=W/2+2;x<W-2;x++)for(int c=0;c<4;c++)
                assertEquals("reference keeps the right pane unchanged",channel(flat,x,y,c),channel(folded,x,y,c));
        }
        renderer.referenceGlass=false;byte[] original=draw();renderer.referenceGlass=true;byte[] reference=draw();
        for(int y=4;y<H-4;y++)for(int x=W/2+2;x<W-2;x++)for(int c=0;c<4;c++)
            assertEquals("A/B preserves fixed-pane image detail",channel(original,x,y,c),channel(reference,x,y,c));
        renderer.referenceGlass=false;assertArrayEquals("A/B does not mutate original renderer state",original,draw());
    }
    @Test public void referenceProjectionDoesNotFlashAtNinetyWhenLit(){
        renderer.referenceGlass=true;renderer.coverMaxAngle=180;renderer.split=0;renderer.moveRight=true;
        // Isolate geometric continuity; the independent fade is explicitly fully lit.
        renderer.coverBrightness=1;renderer.blur=.2f;
        int previous=-1;
        for(float angle:new float[]{88,89,89.9f,90,90.1f,91,100,130,180}){
            renderer.hinge=FoldMath.coverHinge(angle);renderer.visualTilt=angle;
            byte[] pixels=draw();int value=channel(pixels,W/2,H/2,2);
            assertTrue("glass retains image color rather than a black shutter",value>40);
            if(previous>=0)assertTrue("no discontinuity at 90 degrees",Math.abs(value-previous)<15);
            previous=value;assertArrayEquals("stable when paused",pixels,draw());
        }
    }
    @Test public void referenceCoverFocusMovesFromFarEdgeTowardHinge(){
        Bitmap stripes=Bitmap.createBitmap(W,H,Bitmap.Config.ARGB_8888);
        for(int y=0;y<H;y++)for(int x=0;x<W;x++)stripes.setPixel(x,y,y/4%2==0?Color.WHITE:Color.BLACK);
        renderer.setImage(stripes);renderer.referenceGlass=true;renderer.coverMaxAngle=100;
        renderer.split=0;renderer.moveRight=true;renderer.visualTilt=0;renderer.coverBrightness=1;renderer.blur=.2f;
        renderer.hinge=FoldMath.coverHinge(36);byte[] early=draw();
        renderer.hinge=FoldMath.coverHinge(90);byte[] later=draw();
        assertTrue("far edge loses detail first",detail(early,208,216)<detail(early,36,44)*.6);
        assertTrue("focus boundary advances toward the hinge",detail(later,116,124)<detail(early,116,124)*.6);
    }
    @Test public void compressionOnlyContractsCoverRightEdge(){
        renderer.referenceGlass=true;renderer.coverMaxAngle=100;renderer.split=0;renderer.moveRight=true;
        renderer.hinge=FoldMath.coverHinge(100);renderer.visualTilt=60;renderer.blur=0;renderer.coverBrightness=1;
        renderer.edgeDeformation=0;byte[] wide=draw();renderer.edgeDeformation=1;byte[] narrow=draw();
        int wideEdge=0,narrowEdge=0;for(int x=0;x<W;x++){
            if(channel(wide,x,H/2,2)>30)wideEdge=x;if(channel(narrow,x,H/2,2)>30)narrowEdge=x;
        }
        assertTrue("right edge visibly contracts",narrowEdge<wideEdge-20);
        assertEquals("left edge stays anchored",channel(wide,1,H/2,2),channel(narrow,1,H/2,2),1);
        renderer.coverMaxAngle=-1;renderer.split=.5f;renderer.moveRight=false;renderer.hinge=110;
        renderer.edgeDeformation=0;byte[] inner=draw();renderer.edgeDeformation=1;
        assertArrayEquals("cover compression must not change either inner half",inner,draw());
    }
    @Test public void referenceBrightnessHonorsAllFourStartsAndReachesBlack(){
        renderer.referenceGlass=true;renderer.visualTilt=0;renderer.blur=1;renderer.coverMaxAngle=100;
        renderer.split=0;renderer.moveRight=true;renderer.coverBrightness=1;byte[] fullCover=draw();
        CoverDimming cover=new CoverDimming();
        for(float a:new float[]{0,89,134.5f,180,88,64,40}){
            renderer.hinge=FoldMath.coverHinge(a);renderer.coverBrightness=cover.step(a,89,88);
            byte[] pixels=draw();
            assertEquals("cover GPU follows the configured opening/closing envelope",
                channel(fullCover,W/2,H/2,2)*renderer.coverBrightness,channel(pixels,W/2,H/2,2),2);
            if(a==180||a==88)assertEquals("no brightness floor",0,channel(pixels,W/2,H/2,2));
        }
        renderer.coverMaxAngle=-1;renderer.split=.5f;renderer.moveRight=false;renderer.innerRevealEnabled=true;
        renderer.innerBrightness=1;byte[] fullInner=draw();InnerDimming inner=new InnerDimming();
        for(float a:new float[]{0,61,120.5f,180,76,38,0}){
            renderer.hinge=a;renderer.innerBrightness=inner.step(a,61,76);byte[] pixels=draw();
            assertEquals("inner left GPU follows its independent opening/closing envelope",
                channel(fullInner,W/4,H/2,2)*renderer.innerBrightness,channel(pixels,W/4,H/2,2),2);
            if(a==0||a==61)assertEquals(0,channel(pixels,W/4,H/2,2));
            for(int y=3;y<H-3;y++)for(int x=W/2+2;x<W-2;x++)for(int c=0;c<4;c++)
                assertEquals("inner right never dims",channel(fullInner,x,y,c),channel(pixels,x,y,c));
        }
    }
    @After public void cleanup(){if(display!=null){EGL14.eglMakeCurrent(display,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_CONTEXT);if(surface!=null)EGL14.eglDestroySurface(display,surface);if(context!=null)EGL14.eglDestroyContext(display,context);EGL14.eglTerminate(display);}}
}
