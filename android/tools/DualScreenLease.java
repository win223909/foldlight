import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/** Shell-owned local/USB request. No sticky cmd override; loss of heartbeats cancels it. */
public final class DualScreenLease {
    private static byte[] readBytes(java.io.InputStream input)throws java.io.IOException {
        java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();byte[] bytes=new byte[4096];int n;
        while((n=input.read(bytes))!=-1)out.write(bytes,0,n);return out.toByteArray();
    }
    /** Dump through Binder instead of spawning dumpsys during a physical display switch. */
    private static String deviceSnapshot()throws Exception {
        android.os.IBinder service=(android.os.IBinder)Class.forName("android.os.ServiceManager")
            .getMethod("getService",String.class).invoke(null,"device_state");
        if(service==null)throw new IllegalStateException("device_state unavailable");
        android.os.ParcelFileDescriptor[] pipe=android.os.ParcelFileDescriptor.createPipe();
        java.util.concurrent.FutureTask<byte[]> read=new java.util.concurrent.FutureTask<>(()->{
            try(java.io.InputStream in=new android.os.ParcelFileDescriptor.AutoCloseInputStream(pipe[0])){return readBytes(in);}
        });
        Thread reader=new Thread(read,"Foldlight state snapshot");reader.setDaemon(true);reader.start();
        try{
            try{service.dump(pipe[1].getFileDescriptor(),new String[0]);}finally{pipe[1].close();}
            return new String(read.get(200,java.util.concurrent.TimeUnit.MILLISECONDS),java.nio.charset.StandardCharsets.UTF_8);
        }finally{pipe[0].close();read.cancel(true);}
    }
    private static void event(String message){android.util.Log.i("FoldlightLease",android.os.SystemClock.elapsedRealtime()+" "+message);}
    /** Samsung's token-scoped display request expires unless refreshed; it changes no setting. */
    private static final class DisplayPowerLease {
        private final android.os.IBinder token=new android.os.Binder();
        private final Context context;
        private Object service;
        private Method request;
        private boolean held,unavailable;
        DisplayPowerLease(Context context){this.context=context;}
        void refresh(){
            if(unavailable)return;
            try{
                if(!held){
                    android.hardware.display.DisplayManager displays=(android.hardware.display.DisplayManager)context.getSystemService(Context.DISPLAY_SERVICE);
                    android.view.Display inner=displays.getDisplay(1);
                    if(inner==null)return;
                    android.view.Display.Mode mode=inner.getMode();
                    if(mode.getPhysicalWidth()!=2448||mode.getPhysicalHeight()!=1848)return;
                    android.os.IBinder binder=(android.os.IBinder)Class.forName("android.os.ServiceManager").getMethod("getService",String.class).invoke(null,"display");
                    service=Class.forName("android.hardware.display.IDisplayManager$Stub").getMethod("asInterface",android.os.IBinder.class).invoke(null,binder);
                    request=Class.forName("android.hardware.display.IDisplayManager").getMethod("setDisplayStateOverrideWithDisplayId",android.os.IBinder.class,int.class,int.class,int.class);
                }
                request.invoke(service,token,android.view.Display.STATE_ON,1,750);
                if(!held)event("inner power lease acquired, expiry_ms=750");held=true;
            }catch(Exception e){event("inner power lease unavailable: "+e.getClass().getSimpleName());close();unavailable=true;}
        }
        void close(){
            if(held){try{request.invoke(service,token,android.view.Display.STATE_UNKNOWN,1,0);}catch(Exception ignored){}held=false;event("inner power lease released");}
        }
    }
    private static volatile long heartbeat;
    @android.annotation.SuppressLint("WrongConstant") // Shell-only device_state service is a hidden system API.
    public static void main(String[] args) throws Exception {
        if(android.os.Process.myUid()!=2000)throw new SecurityException("ADB shell required");
        if(!android.os.Build.MODEL.equals("SM-F9710"))throw new IllegalStateException("Unverified device");
        Looper.prepareMainLooper();
        Class<?> threadType=Class.forName("android.app.ActivityThread");
        Object thread=threadType.getMethod("systemMain").invoke(null);
        Context context=(Context)threadType.getMethod("getSystemContext").invoke(thread);
        Object manager=context.getSystemService("device_state");
        if(args[0].equals("snapshot")){
            for(int i=0;i<5;i++){long before=SystemClock.elapsedRealtimeNanos();String snapshot=deviceSnapshot();
                System.out.println("snapshot_ms="+(SystemClock.elapsedRealtimeNanos()-before)/1e6+" closed="+snapshot.contains("mBaseState=Optional[DeviceState{identifier=0,")+" noOverride="+snapshot.contains("mOverrideState=Optional.empty"));}
            System.exit(0);return;
        }
        if(args[0].equals("inspect")){
            Object states=manager.getClass().getMethod("getSupportedDeviceStates").invoke(manager);
            for(Object state:(java.util.List<?>)states){
                System.out.println(state);
                for(int property=0;property<=16;property++)try{
                    if((Boolean)state.getClass().getMethod("hasProperty",int.class).invoke(state,property))System.out.println("  property="+property);
                }catch(NoSuchMethodException ignored){break;}
            }
            System.exit(0);return;
        }
        Class<?> requestType=Class.forName("android.hardware.devicestate.DeviceStateRequest");
        int requested=args.length>1?Integer.parseInt(args[1]):4;
        if(requested!=4&&requested!=5)throw new IllegalArgumentException("Concurrent mode required");
        Method cancel=manager.getClass().getMethod("cancelStateRequest");
        DisplayPowerLease power=new DisplayPowerLease(context);
        AtomicBoolean released=new AtomicBoolean();
        Runnable release=()->{if(released.compareAndSet(false,true))try{power.close();cancel.invoke(manager);event("released");}catch(Exception ignored){}};
        Runtime.getRuntime().addShutdownHook(new Thread(release));
        heartbeat=SystemClock.elapsedRealtime();
        int duration=Integer.parseInt(args[0]);
        if(duration<0)throw new IllegalArgumentException("Nonnegative duration required");
        // Zero is explicitly requested by the continuous USB host; the 3-second watchdog remains mandatory.
        long deadline=duration==0?Long.MAX_VALUE:heartbeat+Math.max(1,Math.min(duration,600))*1000L;
        Handler main=new Handler(Looper.getMainLooper());
        Runnable finish=()->{release.run();System.out.println("dual lease released");System.exit(0);};
        Class<?> callbackType=Class.forName("android.hardware.devicestate.DeviceStateRequest$Callback");
        Method submit=manager.getClass().getMethod("requestState",requestType,Executor.class,callbackType);
        Runnable[] acquire=new Runnable[1];
        Object[] current=new Object[1];
        long[] lastRetry={0};
        long[] canceledAt={0};
        Object callback=Proxy.newProxyInstance(callbackType.getClassLoader(),new Class<?>[]{callbackType},(proxy,method,values)->{
            if(method.getDeclaringClass()==Object.class){
                if(method.getName().equals("hashCode"))return System.identityHashCode(proxy);
                if(method.getName().equals("equals"))return proxy==values[0];
                return "Foldlight lease callback";
            }
            if(values==null||values.length==0||values[0]!=current[0])return null;
            if(method.getName().equals("onRequestActivated")){if(requested==5)power.refresh();event("active state="+requested+" restore_ms="+(canceledAt[0]==0?0:SystemClock.elapsedRealtime()-canceledAt[0]));System.out.println("dual lease active state="+requested);System.out.flush();}
            if(method.getName().equals("onRequestCanceled")&&!released.get()){
                canceledAt[0]=SystemClock.elapsedRealtime();event("system canceled");
                main.post(()->{
                    long now=SystemClock.elapsedRealtime();
                    if(released.get())return;
                    if(now-heartbeat>1500||now>=deadline||now-lastRetry[0]<1000){finish.run();return;}
                    // Only restore after physical closure, and never replace another owner's request.
                    try{
                        String state=deviceSnapshot();
                        boolean closed=state.contains("mBaseState=Optional[DeviceState{identifier=0,");
                        if(closed&&state.contains("mOverrideState=Optional.empty")){
                            lastRetry[0]=now;event("restore requested after closed snapshot");acquire[0].run();
                        }else finish.run();
                    }catch(Exception e){System.out.println("restore failed: "+e.getClass().getSimpleName());finish.run();}
                });
            }
            return null;
        });
        acquire[0]=()->{
            try{
                Object builder=requestType.getMethod("newBuilder",int.class).invoke(null,requested);
                current[0]=builder.getClass().getMethod("build").invoke(builder);
                submit.invoke(manager,current[0],(Executor)main::post,callback);
            }catch(Exception e){System.out.println("dual request failed: "+e);finish.run();}
        };
        Thread reader=new Thread(()->{try{while(System.in.read()!=-1)heartbeat=SystemClock.elapsedRealtime();}catch(Exception ignored){}main.post(finish);});
        reader.setDaemon(true);reader.start();
        acquire[0].run();
        main.postDelayed(new Runnable(){public void run(){
            long now=SystemClock.elapsedRealtime();
            if(now-heartbeat>3000||now>=deadline){finish.run();return;}
            if(requested==5)power.refresh();
            main.postDelayed(this,250);
        }},250);
        System.out.println("dual lease requested; heartbeat timeout 3 s");System.out.flush();
        Looper.loop();
    }
}
