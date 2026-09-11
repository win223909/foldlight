package com.zksaga.foldlight;

import android.app.Presentation;
import android.content.Context;
import android.graphics.Bitmap;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.view.*;

/** Own picture on the secondary built-in panel while the USB test holds concurrent display mode. */
final class SecondaryPresentation extends Presentation {
    final FoldRenderer renderer;
    final boolean cover;
    private GLSurfaceView surface;
    private final Runnable settings;
    SecondaryPresentation(Context context, Display display, Bitmap image, float angle, boolean cover,float coverMaxAngle,float innerStrength,float frost,Runnable settings) {
        super(context,display);
        this.cover=cover;this.settings=settings;renderer=new FoldRenderer(image);
        renderer.hinge=cover?FoldMath.coverHinge(angle):angle;renderer.split=cover?0:.5f;renderer.strength=innerStrength;renderer.coverMaxAngle=cover?coverMaxAngle:-1;renderer.blur=frost;renderer.moveRight=cover;renderer.displayRotation=display.getRotation();
    }
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        Window window=getWindow();
        if(window!=null){
            window.setDecorFitsSystemWindows(false);
            window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
            WindowManager.LayoutParams params=window.getAttributes();
            params.screenOrientation=android.content.pm.ActivityInfo.SCREEN_ORIENTATION_NOSENSOR;
            params.rotationAnimation=WindowManager.LayoutParams.ROTATION_ANIMATION_JUMPCUT;
            params.layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
            window.setAttributes(params);
        }
        surface=new FoldSurface(getContext());surface.setEGLContextClientVersion(3);
        surface.setPreserveEGLContextOnPause(true);surface.setRenderer(renderer);
        surface.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
        ((FoldSurface)surface).setSettingsAction(settings);
        surface.getHolder().addCallback(new SurfaceHolder.Callback(){
            public void surfaceCreated(SurfaceHolder h){h.getSurface().setFrameRate(120,Surface.FRAME_RATE_COMPATIBILITY_DEFAULT);}
            public void surfaceChanged(SurfaceHolder h,int f,int w,int v){renderer.displayRotation=getDisplay().getRotation();surface.requestRender();}
            public void surfaceDestroyed(SurfaceHolder h){}
        });
        setContentView(surface);
        if(window!=null){WindowInsetsController insets=window.getInsetsController();if(insets!=null)insets.hide(WindowInsets.Type.systemBars());}
    }
    void render(float angle,boolean force){
        float target=cover?FoldMath.coverHinge(angle):angle;
        int rotation=getDisplay().getRotation();
        boolean changed=Math.abs(renderer.hinge-target)>.005f||renderer.displayRotation!=rotation;
        renderer.displayRotation=rotation;
        renderer.hinge=target;
        if(surface!=null&&(changed||force))surface.requestRender();
    }
    void setImage(Bitmap image){renderer.setImage(image);if(surface!=null)surface.requestRender();}
    @Override protected void onStop(){if(surface!=null)surface.onPause();super.onStop();}
}
