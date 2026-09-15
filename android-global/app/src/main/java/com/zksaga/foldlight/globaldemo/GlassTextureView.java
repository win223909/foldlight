package com.zksaga.foldlight.globaldemo;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.opengl.*;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.TextureView;
import java.util.concurrent.atomic.AtomicBoolean;

/** A single composited layer; readiness means a buffer reached TextureView, not just GL. */
final class GlassTextureView extends TextureView implements TextureView.SurfaceTextureListener {
    private final HandlerThread thread=new HandlerThread("GlobalGlassGL");
    private final Handler gl;
    private final FoldRenderer renderer;
    private final Runnable firstFrame;
    private final AtomicBoolean queued=new AtomicBoolean();
    private volatile boolean closed;
    private boolean presented,ready;
    private EGLDisplay display=EGL14.EGL_NO_DISPLAY;
    private EGLContext context=EGL14.EGL_NO_CONTEXT;
    private EGLSurface target=EGL14.EGL_NO_SURFACE;

    GlassTextureView(Context context,FoldRenderer renderer,Runnable firstFrame){
        super(context);this.renderer=renderer;this.firstFrame=firstFrame;
        thread.start();gl=new Handler(thread.getLooper());setSurfaceTextureListener(this);
        setOpaque(true);
    }
    @Override public void onSurfaceTextureAvailable(SurfaceTexture texture,int w,int h){
        gl.post(()->{if(closed)return;
            try{
                display=EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
                if(!EGL14.eglInitialize(display,new int[2],0,new int[2],1))throw new IllegalStateException("EGL initialize");
                int[] attrs={EGL14.EGL_RED_SIZE,8,EGL14.EGL_GREEN_SIZE,8,EGL14.EGL_BLUE_SIZE,8,EGL14.EGL_ALPHA_SIZE,8,
                    EGL14.EGL_RENDERABLE_TYPE,0x40,EGL14.EGL_SURFACE_TYPE,EGL14.EGL_WINDOW_BIT,EGL14.EGL_NONE};
                EGLConfig[] configs=new EGLConfig[1];int[] count=new int[1];
                if(!EGL14.eglChooseConfig(display,attrs,0,configs,0,1,count,0)||count[0]==0)throw new IllegalStateException("EGL config");
                context=EGL14.eglCreateContext(display,configs[0],EGL14.EGL_NO_CONTEXT,new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION,3,EGL14.EGL_NONE},0);
                target=EGL14.eglCreateWindowSurface(display,configs[0],texture,new int[]{EGL14.EGL_NONE},0);
                if(!EGL14.eglMakeCurrent(display,target,target,context))throw new IllegalStateException("EGL current");
                renderer.onSurfaceCreated(null,null);renderer.onSurfaceChanged(null,w,h);ready=true;draw();
            }catch(RuntimeException e){renderer.error="图形初始化失败："+e.getMessage();}
        });
    }
    @Override public void onSurfaceTextureSizeChanged(SurfaceTexture texture,int w,int h){gl.post(()->{if(ready&&!closed)renderer.onSurfaceChanged(null,w,h);});}
    @Override public void onSurfaceTextureUpdated(SurfaceTexture texture){if(!closed&&!presented){presented=true;firstFrame.run();}}
    @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture texture){
        // Release the producer on its owning thread before releasing the native consumer.
        gl.post(()->{releaseEgl();texture.release();});return false;
    }
    void requestRender(){if(!closed&&queued.compareAndSet(false,true))gl.post(()->{queued.set(false);draw();});}
    private void draw(){if(!closed&&ready){renderer.onDrawFrame(null);if(!EGL14.eglSwapBuffers(display,target))renderer.error="图形缓冲区提交失败";}}
    void close(Runnable afterRelease){
        closed=true;
        // Called after removal; onSurfaceTextureDestroyed has already queued consumer cleanup.
        gl.post(()->{releaseEgl();afterRelease.run();thread.quitSafely();});
    }
    private void releaseEgl(){
        ready=false;
        if(display!=EGL14.EGL_NO_DISPLAY){
            EGL14.eglMakeCurrent(display,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_CONTEXT);
            if(target!=EGL14.EGL_NO_SURFACE)EGL14.eglDestroySurface(display,target);
            if(context!=EGL14.EGL_NO_CONTEXT)EGL14.eglDestroyContext(display,context);
            EGL14.eglTerminate(display);EGL14.eglReleaseThread();
        }
        display=EGL14.EGL_NO_DISPLAY;target=EGL14.EGL_NO_SURFACE;context=EGL14.EGL_NO_CONTEXT;
    }
}
