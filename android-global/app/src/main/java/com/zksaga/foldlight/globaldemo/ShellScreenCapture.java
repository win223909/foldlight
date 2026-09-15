/* Adapted from bunkaich/Folduo, Copyright (c) 2026 bunkaich. MIT; see licenses/Folduo-MIT.txt. */
package com.zksaga.foldlight.globaldemo;
import android.os.*;import android.view.SurfaceControl;import android.graphics.Bitmap;import android.hardware.HardwareBuffer;import java.io.*;
final class ShellScreenCapture {
    static Bundle captureBehind(int displayId,SurfaceControl[] exclude){
        long token=Binder.clearCallingIdentity();Bundle result=new Bundle();
        try{
            // Never request secure/protected layers. No pixels are written to storage or logs.
            String family="android.window.ScreenCapture";
            try{Class.forName(family+"$CaptureArgs");}catch(ClassNotFoundException changed){family="android.window.ScreenCaptureInternal";}
            Class<?> capture=Class.forName(family);
            Class<?> argsClass=Class.forName(family+"$CaptureArgs");
            Class<?> builderClass=Class.forName(family+"$CaptureArgs$Builder");
            Object builder=builderClass.getConstructor().newInstance();
            if(family.endsWith("Internal")){
                // New Samsung/Android builds use explicit policies. Reject protected frames.
                Class<?> policies=Class.forName("android.window.ScreenCapture$ScreenCaptureParams");
                builderClass.getMethod("setSecureContentPolicy",int.class).invoke(builder,policies.getField("SECURE_CONTENT_POLICY_THROW_EXCEPTION").getInt(null));
                builderClass.getMethod("setProtectedContentPolicy",int.class).invoke(builder,policies.getField("PROTECTED_CONTENT_POLICY_THROW_EXCEPTION").getInt(null));
                builderClass.getMethod("setIncludeSystemOverlays",boolean.class).invoke(builder,true);
            }else{
                builderClass.getMethod("setCaptureSecureLayers",boolean.class).invoke(builder,false);
                builderClass.getMethod("setAllowProtected",boolean.class).invoke(builder,false);
            }
            if(exclude!=null&&exclude.length>0)builderClass.getMethod("setExcludeLayers",SurfaceControl[].class).invoke(builder,(Object)exclude);
            Object args=builderClass.getMethod("build").invoke(builder);
            Object listener=capture.getMethod("createSyncCaptureListener").invoke(null);
            IBinder binder=(IBinder)Class.forName("android.os.ServiceManager").getMethod("getService",String.class).invoke(null,"window");
            Object wm=Class.forName("android.view.IWindowManager$Stub").getMethod("asInterface",IBinder.class).invoke(null,binder);
            Class.forName("android.view.IWindowManager").getMethod("captureDisplay",int.class,argsClass,Class.forName(family+"$ScreenCaptureListener")).invoke(wm,displayId,args,listener);
            Object buffer=Class.forName(family+"$SynchronousScreenCaptureListener").getMethod("getBuffer").invoke(listener);
            if(buffer==null)throw new IOException("@folduo/err_no_frame");
            Class<?> bufferClass=Class.forName(family+"$ScreenshotHardwareBuffer");
            HardwareBuffer hardware=(HardwareBuffer)bufferClass.getMethod("getHardwareBuffer").invoke(buffer);
            try{
                if((boolean)bufferClass.getMethod("containsSecureLayers").invoke(buffer))throw new SecurityException("@folduo/err_protected_frame");
                Bitmap bitmap=(Bitmap)bufferClass.getMethod("asBitmap").invoke(buffer);
                if(bitmap==null)throw new IOException("@folduo/err_empty_frame");
                result.putParcelable("frame",bitmap);result.putBoolean("ok",true);
            }finally{if(hardware!=null)hardware.close();}
        }catch(Exception e){result.putString("error",e.toString());}finally{Binder.restoreCallingIdentity(token);}
        return result;
    }
}
