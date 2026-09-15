package com.zksaga.foldlight.globaldemo;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.view.*;
import android.widget.FrameLayout;

/** A short-lived secondary cover view, using this fold's screenshot, not an old page. */
final class EarlyCoverOverlay {
    private final WindowManager manager;
    private final FrameLayout root;
    private final GlassTextureView surface;
    private final FoldRenderer renderer;
    private final Bitmap image;
    private final CoverDimming dimming=new CoverDimming();
    private boolean closed;
    EarlyCoverOverlay(Context service,Display display,Bitmap source,float angle){
        Context c=service.createDisplayContext(display).createWindowContext(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,null);
        manager=c.getSystemService(WindowManager.class);
        image=source.copy(Bitmap.Config.ARGB_8888,false);
        renderer=new FoldRenderer(image);renderer.referenceGlass=true;renderer.edgeDeformation=EffectDefaults.EDGE;
        renderer.blur=.55f;renderer.split=0;renderer.moveRight=true;renderer.coverMaxAngle=100;
        dimming.restore(0);
        root=new FrameLayout(c);root.setBackgroundColor(Color.BLACK);
        surface=new GlassTextureView(c,renderer,()->android.util.Log.i("FoldlightGlobal","early cover presented"));
        root.addView(surface,new FrameLayout.LayoutParams(-1,-1));
        WindowManager.LayoutParams p=new WindowManager.LayoutParams(-1,-1,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN|WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,PixelFormat.OPAQUE);
        p.gravity=Gravity.TOP|Gravity.START;p.setFitInsetsTypes(0);p.screenOrientation=ActivityInfo.SCREEN_ORIENTATION_NOSENSOR;
        p.layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;p.setTitle("Foldlight Early Cover");
        try{manager.addView(root,p);step(angle);}catch(RuntimeException e){close();throw e;}
    }
    boolean healthy(){return !closed&&renderer.error.isEmpty();}
    void step(float angle){
        if(closed)return;renderer.hinge=FoldMath.coverHinge(angle);renderer.visualTilt=FoldMath.coverTilt(angle,100);
        renderer.coverBrightness=dimming.step(angle,EffectDefaults.DARK_START,EffectDefaults.LIGHT_START);surface.requestRender();
    }
    void close(){if(closed)return;closed=true;try{manager.removeViewImmediate(root);}catch(RuntimeException ignored){}surface.close(image::recycle);}
}
