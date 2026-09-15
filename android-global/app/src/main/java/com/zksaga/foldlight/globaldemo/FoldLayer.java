package com.zksaga.foldlight.globaldemo;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.graphics.*;
import android.view.*;
import android.widget.*;

/** Own embedded surface can be excluded from capture without removing the visible cover. */
final class FoldLayer extends SurfaceView implements SurfaceHolder.Callback {
    final int displayId;final boolean inner;final long serial;
    private final WindowManager wm;private final Bitmap image;
    private final FoldRenderer renderer;private DirectGlassSurface glass;
    private final Runnable committed;
    private final java.util.concurrent.atomic.AtomicInteger imageUsers=new java.util.concurrent.atomic.AtomicInteger(1);
    private final CoverDimming coverDimming=new CoverDimming();private final InnerDimming innerDimming=new InnerDimming();
    private EffectParameters settings;
    private boolean closed,announced;boolean ready;private float angle;
    FoldLayer(Context service,Display display,Bitmap source,boolean inner,long serial,float angle,EffectParameters settings,Runnable committed){
        super(service.createDisplayContext(display).createWindowContext(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,null));
        displayId=display.getDisplayId();this.inner=inner;this.serial=serial;this.angle=angle;this.committed=committed;this.settings=settings;
        image=source.copy(Bitmap.Config.ARGB_8888,false);renderer=new FoldRenderer(image);renderer.referenceGlass=true;renderer.edgeDeformation=settings.value(EffectParameters.Control.EDGE);
        renderer.blur=settings.value(EffectParameters.Control.BLUR);renderer.split=inner?.5f:0;renderer.moveRight=!inner;renderer.innerRevealEnabled=inner;renderer.coverMaxAngle=inner?-1:100;
        coverDimming.restore(angle>settings.value(EffectParameters.Control.COVER_LIGHT)?0:1);innerDimming.restore(angle<settings.value(EffectParameters.Control.INNER_LIGHT)?0:1);
        wm=getContext().getSystemService(WindowManager.class);
        // Keep the new native surface invisible until its glass buffer is committed.
        setAlpha(0);setZOrderOnTop(true);getHolder().setFormat(PixelFormat.TRANSLUCENT);getHolder().addCallback(this);
        setOnTouchListener((v,e)->true);
    }
    void attach(){
        WindowManager.LayoutParams p=new WindowManager.LayoutParams(-1,-1,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN|WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,PixelFormat.TRANSLUCENT);
        p.gravity=Gravity.TOP|Gravity.LEFT;p.setFitInsetsTypes(0);p.screenOrientation=ActivityInfo.SCREEN_ORIENTATION_LOCKED;p.windowAnimations=0;p.preferredRefreshRate=120;
        p.layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;p.setTitle("Foldlight stable transition");wm.addView(this,p);
    }
    public void surfaceCreated(SurfaceHolder holder){
        if(closed)return;ready=false;setAlpha(0);imageUsers.incrementAndGet();
        DirectGlassSurface[] producer=new DirectGlassSurface[1];
        producer[0]=new DirectGlassSurface(renderer,()->{
            if(closed||glass!=producer[0])return;setAlpha(1);
            postOnAnimation(()->postOnAnimation(()->{
                if(closed||glass!=producer[0])return;ready=true;
                if(!announced){announced=true;committed.run();}
            }));
        },this::releaseImage);
        glass=producer[0];step(angle);
        glass.start(holder.getSurface(),getWidth(),getHeight());
    }
    public void surfaceChanged(SurfaceHolder h,int format,int w,int height){if(glass!=null)glass.resize(w,height);}
    public void surfaceDestroyed(SurfaceHolder h){ready=false;stopProducer();}
    private void stopProducer(){DirectGlassSurface old=glass;glass=null;if(old!=null)old.close();}
    private void releaseImage(){if(imageUsers.decrementAndGet()==0)image.recycle();}
    boolean healthy(){return !closed&&renderer.error.isEmpty();}
    void parameters(EffectParameters next){settings=next;renderer.edgeDeformation=next.value(EffectParameters.Control.EDGE);renderer.blur=next.value(EffectParameters.Control.BLUR);step(angle);}
    void step(float value){
        angle=value;renderer.hinge=inner?value:FoldMath.coverHinge(value);renderer.visualTilt=inner?FoldMath.panelTilt(value,.9f):FoldMath.coverTilt(value,100);
        renderer.coverBrightness=inner?1:coverDimming.step(value,settings.value(EffectParameters.Control.COVER_DARK),settings.value(EffectParameters.Control.COVER_LIGHT));
        renderer.innerBrightness=inner?innerDimming.step(value,settings.value(EffectParameters.Control.INNER_LIGHT),settings.value(EffectParameters.Control.INNER_DARK),120):1;
        if(glass!=null)glass.requestRender();
    }
    void close(){if(closed)return;closed=true;animate().cancel();try{wm.removeViewImmediate(this);}catch(RuntimeException ignored){}stopProducer();releaseImage();}
}
