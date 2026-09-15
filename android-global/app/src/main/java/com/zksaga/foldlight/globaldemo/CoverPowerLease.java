package com.zksaga.foldlight.globaldemo;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.os.*;
import android.view.Display;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

/** Owner-scoped concurrent INNER mode: enables the cover without swapping the primary panel. */
final class CoverPowerLease {
    private final Context context;
    private Object manager,windowService,requestObject,callback;
    private Method cancel,keyguardLocked;
    private boolean requested,unavailable;
    private volatile boolean rejected;
    private long lastStateReport;
    CoverPowerLease(Context context){this.context=context;}
    @android.annotation.SuppressLint("WrongConstant") // Verified shell-only device_state service.
    synchronized void update(boolean wanted){
        if(!wanted){close();rejected=false;return;}
        if(unavailable||rejected)return;
        try{
            DisplayManager dm=context.getSystemService(DisplayManager.class);
            Display primary=dm.getDisplay(0),secondary=dm.getDisplay(1);
            boolean allowed=Build.MODEL.equals("SM-F9710")&&primary!=null&&secondary!=null
                &&primary.getState()==Display.STATE_ON&&primary.getMode().getPhysicalWidth()==2448
                &&secondary.getMode().getPhysicalWidth()==1248
                &&context.getSystemService(PowerManager.class).isInteractive()&&!isKeyguardLocked();
            if(!allowed){close();return;}
            if(!requested){
                // Do not supersede another app's device-state request (including original Foldlight).
                String state=deviceSnapshot();
                if(!state.contains("mOverrideState=Optional.empty")||!state.contains("mPendingState=Optional.empty")
                    ||state.contains("mBaseState=Optional[DeviceState{identifier=0,"))return;
                manager=context.getSystemService("device_state");
                Class<?> requestType=Class.forName("android.hardware.devicestate.DeviceStateRequest");
                Class<?> callbackType=Class.forName("android.hardware.devicestate.DeviceStateRequest$Callback");
                Object builder=requestType.getMethod("newBuilder",int.class).invoke(null,4);
                requestObject=builder.getClass().getMethod("build").invoke(builder);
                cancel=manager.getClass().getMethod("cancelStateRequest");
                callback=Proxy.newProxyInstance(callbackType.getClassLoader(),new Class<?>[]{callbackType},(proxy,method,args)->{
                    if(method.getDeclaringClass()==Object.class){
                        if(method.getName().equals("hashCode"))return System.identityHashCode(proxy);
                        if(method.getName().equals("equals"))return proxy==args[0];
                        return "Global concurrent cover callback";
                    }
                    if(args==null||args.length==0||args[0]!=requestObject)return null;
                    if(method.getName().equals("onRequestActivated"))android.util.Log.i("FoldlightGlobalPower","concurrent inner ACTIVE");
                    if(method.getName().equals("onRequestCanceled")){rejected=true;android.util.Log.i("FoldlightGlobalPower","concurrent inner canceled by system");}
                    return null;
                });
                requested=true;
                manager.getClass().getMethod("requestState",requestType,Executor.class,callbackType)
                    .invoke(manager,requestObject,(Executor)Runnable::run,callback);
                android.util.Log.i("FoldlightGlobalPower","concurrent inner requested");
            }
            long now=SystemClock.elapsedRealtime();if(now-lastStateReport>500){lastStateReport=now;
                android.util.Log.i("FoldlightGlobalPower","cover state="+secondary.getState()+" primaryWidth="+primary.getMode().getPhysicalWidth());}
        }catch(Exception e){android.util.Log.w("FoldlightGlobalPower","concurrent cover unavailable",e);close();unavailable=true;}
    }
    // Shizuku has no ApplicationSharedMemory; do not construct KeyguardManager here.
    private boolean isKeyguardLocked()throws Exception{
        if(windowService==null){
            IBinder binder=(IBinder)Class.forName("android.os.ServiceManager").getMethod("getService",String.class).invoke(null,"window");
            windowService=Class.forName("android.view.IWindowManager$Stub").getMethod("asInterface",IBinder.class).invoke(null,binder);
            keyguardLocked=Class.forName("android.view.IWindowManager").getMethod("isKeyguardLocked");
        }
        return (Boolean)keyguardLocked.invoke(windowService);
    }
    private static String deviceSnapshot()throws Exception{
        IBinder binder=(IBinder)Class.forName("android.os.ServiceManager").getMethod("getService",String.class).invoke(null,"device_state");
        ParcelFileDescriptor[] pipe=ParcelFileDescriptor.createPipe();
        FutureTask<byte[]> read=new FutureTask<>(()->{try(java.io.InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(pipe[0])){java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();byte[] buffer=new byte[4096];int count;while((count=in.read(buffer))!=-1)out.write(buffer,0,count);return out.toByteArray();}});
        Thread reader=new Thread(read,"Global display state");reader.setDaemon(true);reader.start();
        try{try{binder.dump(pipe[1].getFileDescriptor(),new String[0]);}finally{pipe[1].close();}
            return new String(read.get(300,TimeUnit.MILLISECONDS),StandardCharsets.UTF_8);
        }finally{pipe[0].close();read.cancel(true);}
    }
    synchronized void close(){
        if(!requested)return;requested=false;
        try{cancel.invoke(manager);}catch(Exception ignored){}
        android.util.Log.i("FoldlightGlobalPower","concurrent inner released");
    }
}
