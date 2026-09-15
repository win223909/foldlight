package com.zksaga.foldlight.globaldemo;

import android.content.Context;
import android.os.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

/** Angle bridge with an owner-scoped cover-primary session; no persistent settings writes. */
public final class LocalHingeService extends ILocalHingeService.Stub {
    private final int owner;
    private volatile boolean closed,active,earlyCover;
    private final StableDisplaySession coverPower;
    private final Thread powerWorker;
    private volatile long heartbeat;
    private volatile java.lang.Process logs;
    private volatile ILocalHingeCallback callback;
    private final Thread reader;
    private final IBinder.DeathRecipient death=this::shutdown;
    public LocalHingeService(Context context){
        if(android.os.Process.myUid()!=2000)throw new SecurityException("Shell required");
        if(FoldDeviceProfile.forModel(Build.MODEL)==null)throw new IllegalStateException("Unverified device");
        owner=context.getApplicationInfo().uid;
        coverPower=new StableDisplaySession(context);
        powerWorker=new Thread(()->{try{while(!closed){coverPower.update(fresh()&&earlyCover);Thread.sleep(125);}}catch(InterruptedException ignored){}finally{coverPower.close();}},"Global cover power");
        powerWorker.start();
        reader=new Thread(this::read,"Global hinge samples");reader.start();
    }
    private void check(){int uid=Binder.getCallingUid();if(uid!=owner&&uid!=2000)throw new SecurityException("Unexpected caller");}
    public synchronized void start(ILocalHingeCallback next){
        check();if(closed||next==null)throw new IllegalStateException();
        if(callback!=null)callback.asBinder().unlinkToDeath(death,0);
        callback=next;heartbeat=SystemClock.elapsedRealtime();
        try{next.asBinder().linkToDeath(death,0);next.status("本地连续角度已连接");}catch(RemoteException e){shutdown();}
    }
    public void heartbeat(boolean sampling,boolean requestedCover){check();heartbeat=SystemClock.elapsedRealtime();active=sampling;earlyCover=sampling&&requestedCover;if(!active&&logs!=null)logs.destroy();}
    public Bundle sessionState(){check();long token=Binder.clearCallingIdentity();try{Bundle b=new Bundle();b.putBoolean("ready",coverPower.ready());b.putBoolean("physicallyClosed",coverPower.physicallyClosed());return b;}finally{Binder.restoreCallingIdentity(token);}}
    public Bundle moveTask(int source,int destination){check();long token=Binder.clearCallingIdentity();try{if(!fresh())throw new IllegalStateException("Expired");return coverPower.move(source,destination);}catch(Exception e){Bundle b=new Bundle();b.putString("error",e.getMessage());return b;}finally{Binder.restoreCallingIdentity(token);}}
    public Bundle captureBehind(int displayId,android.view.SurfaceControl[] excluded){
        check();long token=Binder.clearCallingIdentity();try{if(!fresh()||!coverPower.ready()||!coverPower.unlocked()||displayId<0||displayId>1)throw new IllegalStateException("Capture unavailable");return ShellScreenCapture.captureBehind(displayId,excluded);}catch(Exception e){Bundle b=new Bundle();b.putString("error",e.getMessage());return b;}finally{Binder.restoreCallingIdentity(token);}
    }
    public Bundle windowState(int displayId){
        check();Bundle b=new Bundle();if(!fresh()||displayId<0||displayId>1)return b;
        java.lang.Process process=null;try{
            process=new ProcessBuilder("dumpsys","window","visible-apps").start();
            final java.lang.Process owned=process;
            java.util.concurrent.FutureTask<String> task=new java.util.concurrent.FutureTask<>(()->{try(InputStream in=owned.getInputStream()){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] bytes=new byte[4096];int count;while((count=in.read(bytes))!=-1)out.write(bytes,0,count);return new String(out.toByteArray(),StandardCharsets.UTF_8);}});
            Thread t=new Thread(task,"Global window geometry");t.setDaemon(true);t.start();
            String dump=task.get(900,java.util.concurrent.TimeUnit.MILLISECONDS);
            WindowReadiness.State state=WindowReadiness.parse(dump,displayId);b.putBoolean("ready",state.ready());b.putString("geometry",state.geometry());
        }catch(Exception e){b.putString("error","Window not ready");}finally{if(process!=null)process.destroy();}return b;
    }
    public void backOnInner(){check();long token=Binder.clearCallingIdentity();try{if(fresh())coverPower.back();}catch(Exception ignored){}finally{Binder.restoreCallingIdentity(token);}}
    public void stop(){check();shutdown();}
    public void destroy(){check();shutdown();System.exit(0);}
    private void shutdown(){closed=true;active=false;if(logs!=null)logs.destroy();reader.interrupt();powerWorker.interrupt();coverPower.close();}
    private boolean fresh(){return !closed&&active&&callback!=null&&SystemClock.elapsedRealtime()-heartbeat<1500;}
    private void read(){
        long stamp=0;
        while(!closed){
            try{
                if(!fresh()){Thread.sleep(100);continue;}
                java.lang.Process pidProcess=new ProcessBuilder("/system/bin/pidof","android.hardware.sensors-service.multihal").start();
                String pid;
                try(BufferedReader in=new BufferedReader(new InputStreamReader(pidProcess.getInputStream()))){pid=in.readLine();}
                finally{pidProcess.destroy();}
                if(pid==null||!pid.matches("[0-9]+")){Thread.sleep(1500);continue;}
                logs=new ProcessBuilder("/system/bin/logcat","-b","main","--pid="+pid,"--uid=1000","-v","brief","-T","1","-s","sensors-hal:I","*:S")
                    .redirectError(ProcessBuilder.Redirect.to(new File("/dev/null"))).start();
                try(BufferedReader in=new BufferedReader(new InputStreamReader(logs.getInputStream(),StandardCharsets.UTF_8))){
                    String line;while(fresh()&&(line=in.readLine())!=null){
                        HingeLogParser.Sample sample=HingeLogParser.parse(line);
                        if(sample!=null&&sample.nanos>stamp&&HingeLogParser.fresh(sample.nanos,SystemClock.elapsedRealtimeNanos())){
                            stamp=sample.nanos;callback.angle(stamp,sample.angle);
                        }
                    }
                }finally{logs.destroy();logs=null;}
            }catch(InterruptedException e){if(closed)return;}catch(Exception e){try{Thread.sleep(1000);}catch(InterruptedException ignored){}}
        }
    }
}
