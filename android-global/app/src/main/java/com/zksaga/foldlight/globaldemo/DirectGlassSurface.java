package com.zksaga.foldlight.globaldemo;

import android.opengl.*;
import android.os.*;
import android.view.Surface;
import java.util.concurrent.atomic.AtomicBoolean;

/** Each physical panel owns its EGL producer; no shared HWUI/TextureView composition. */
final class DirectGlassSurface {
    private final HandlerThread thread=new HandlerThread("GlobalDirectGL");
    private final Handler gl,main=new Handler(Looper.getMainLooper());
    private final FoldRenderer renderer;
    private final Runnable firstBuffer,released;
    private final AtomicBoolean queued=new AtomicBoolean(),closed=new AtomicBoolean();
    private boolean ready,presented;
    private EGLDisplay display=EGL14.EGL_NO_DISPLAY;
    private EGLContext context=EGL14.EGL_NO_CONTEXT;
    private EGLSurface target=EGL14.EGL_NO_SURFACE;
    private long frames,maxDrawNs,slowSwaps;
    DirectGlassSurface(FoldRenderer renderer,Runnable firstBuffer,Runnable released){
        this.renderer=renderer;this.firstBuffer=firstBuffer;this.released=released;
        thread.start();gl=new Handler(thread.getLooper());
    }
    void start(Surface surface,int w,int h){gl.post(()->{
        if(closed.get())return;
        try{
            display=EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
            if(!EGL14.eglInitialize(display,new int[2],0,new int[2],1))throw new IllegalStateException("EGL initialize");
            int[] attrs={EGL14.EGL_RED_SIZE,8,EGL14.EGL_GREEN_SIZE,8,EGL14.EGL_BLUE_SIZE,8,EGL14.EGL_ALPHA_SIZE,8,
                EGL14.EGL_RENDERABLE_TYPE,0x40,EGL14.EGL_SURFACE_TYPE,EGL14.EGL_WINDOW_BIT,EGL14.EGL_NONE};
            EGLConfig[] configs=new EGLConfig[1];int[] count=new int[1];
            if(!EGL14.eglChooseConfig(display,attrs,0,configs,0,1,count,0)||count[0]==0)throw new IllegalStateException("EGL config");
            context=EGL14.eglCreateContext(display,configs[0],EGL14.EGL_NO_CONTEXT,new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION,3,EGL14.EGL_NONE},0);
            target=EGL14.eglCreateWindowSurface(display,configs[0],surface,new int[]{EGL14.EGL_NONE},0);
            if(!EGL14.eglMakeCurrent(display,target,target,context))throw new IllegalStateException("EGL current");
            renderer.onSurfaceCreated(null,null);renderer.onSurfaceChanged(null,w,h);ready=true;draw();
        }catch(RuntimeException e){if(!closed.get())renderer.error="图形初始化失败："+e.getMessage();}
    });}
    void resize(int w,int h){gl.post(()->{if(ready&&!closed.get())renderer.onSurfaceChanged(null,w,h);});}
    void requestRender(){if(!closed.get()&&queued.compareAndSet(false,true))gl.post(()->{queued.set(false);draw();});}
    private void draw(){
        if(closed.get()||!ready)return;
        long start=System.nanoTime();renderer.onDrawFrame(null);
        boolean swapped=EGL14.eglSwapBuffers(display,target);long elapsed=System.nanoTime()-start;
        frames++;maxDrawNs=Math.max(maxDrawNs,elapsed);if(elapsed>33000000)slowSwaps++;
        if(!swapped&&!closed.get()){renderer.error="图形缓冲区提交失败";return;}
        if(!presented&&swapped&&renderer.error.isEmpty()&&!closed.get()){
            // One startup fence, on this panel's GL thread only. Later frames never
            // wait for the other panel. Reveal after buffer submission and GPU completion.
            GLES30.glFinish();presented=true;main.post(()->{if(!closed.get())firstBuffer.run();});
        }
    }
    void close(){
        if(!closed.compareAndSet(false,true))return;
        gl.post(()->{
            ready=false;
            if(display!=EGL14.EGL_NO_DISPLAY){
                EGL14.eglMakeCurrent(display,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_CONTEXT);
                if(target!=EGL14.EGL_NO_SURFACE)EGL14.eglDestroySurface(display,target);
                if(context!=EGL14.EGL_NO_CONTEXT)EGL14.eglDestroyContext(display,context);
                EGL14.eglTerminate(display);EGL14.eglReleaseThread();
            }
            android.util.Log.i("FoldlightStable","direct surface retired frames="+frames+" slowDraws="+slowSwaps+" maxDrawMs="+(maxDrawNs/1000000));
            released.run();thread.quitSafely();
        });
    }
}
