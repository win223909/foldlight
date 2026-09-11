package com.zksaga.foldlight;

import android.content.Context;
import android.os.*;
import java.lang.Process;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/** Shizuku shell UserService. No socket, network, saved logs, or arbitrary command interface. */
public final class LocalHingeService extends ILocalHingeService.Stub {
    private final String apk;
    private final int owner;
    private volatile ILocalHingeCallback callback;
    private volatile boolean closed,sampling,dualAllowed;
    private volatile long heartbeat;
    private volatile Process logs;
    private final Thread reader,controller;
    private final IBinder.DeathRecipient death=this::shutdown;
    public LocalHingeService(Context context){
        if(android.os.Process.myUid()!=2000)throw new SecurityException("Shell Shizuku required");
        if(!Build.MODEL.equals("SM-F9710"))throw new IllegalStateException("Unverified fold device");
        apk=context.getApplicationInfo().sourceDir;owner=context.getApplicationInfo().uid;
        reader=new Thread(this::read,"Foldlight local angles");controller=new Thread(this::control,"Foldlight local displays");
        reader.start();controller.start();
    }
    private void checkCaller(){int uid=Binder.getCallingUid();if(uid!=owner&&uid!=2000)throw new SecurityException("Unexpected client");}
    @Override public synchronized void start(ILocalHingeCallback value){
        checkCaller();if(closed||value==null)throw new IllegalStateException("Service unavailable");
        if(callback!=null)callback.asBinder().unlinkToDeath(death,0);
        callback=value;heartbeat=SystemClock.elapsedRealtime();
        try{value.asBinder().linkToDeath(death,0);value.status("手机本地辅助已连接");}catch(RemoteException e){shutdown();}
    }
    @Override public void heartbeat(boolean active,boolean dual){checkCaller();heartbeat=SystemClock.elapsedRealtime();sampling=active;dualAllowed=active&&dual;}
    @Override public void stop(){checkCaller();shutdown();}
    @Override public void destroy(){checkCaller();shutdown();try{controller.join(1200);}catch(InterruptedException ignored){}System.exit(0);}
    private void shutdown(){closed=true;sampling=false;dualAllowed=false;Process p=logs;if(p!=null)p.destroy();reader.interrupt();controller.interrupt();}
    private boolean fresh(){return !closed&&callback!=null&&SystemClock.elapsedRealtime()-heartbeat<1500;}
    private void status(String message){ILocalHingeCallback c=callback;if(c!=null)try{c.status(message);}catch(RemoteException e){shutdown();}}
    private static byte[] readBytes(InputStream input)throws IOException {
        ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[4096];int n;
        while((n=input.read(buffer))!=-1)out.write(buffer,0,n);return out.toByteArray();
    }
    private static String command(String... args)throws Exception{
        Process p=new ProcessBuilder(args).redirectErrorStream(true).start();
        try{if(!p.waitFor(1,TimeUnit.SECONDS))throw new IOException("Command timeout");if(p.exitValue()!=0)throw new IOException("Command failed");return new String(readBytes(p.getInputStream()),StandardCharsets.UTF_8);}
        finally{p.destroy();}
    }
    private static void pause(long millis){try{Thread.sleep(millis);}catch(InterruptedException ignored){}}
    private void read(){
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_DISPLAY);
        long lastStamp=0;
        while(!closed){
            if(!fresh()||!sampling){pause(100);continue;}
            try{
                String pid=command("/system/bin/pidof","android.hardware.sensors-service.multihal").trim();
                if(!pid.matches("[0-9]+"))throw new IOException("HAL unavailable");
                Process p=new ProcessBuilder("/system/bin/logcat","-b","main","--pid="+pid,"--uid=1000","-v","brief","-T","1","-s","sensors-hal:I","*:S").redirectError(ProcessBuilder.Redirect.to(new File("/dev/null"))).start();logs=p;
                status("手机本地辅助已连接");
                try(BufferedReader input=new BufferedReader(new InputStreamReader(p.getInputStream(),StandardCharsets.UTF_8))){
                    String line;
                    while(!closed&&fresh()&&sampling&&(line=input.readLine())!=null){
                        HingeLogParser.Sample sample=HingeLogParser.parse(line);
                        if(sample==null||sample.nanos<=lastStamp||!HingeLogParser.fresh(sample.nanos,SystemClock.elapsedRealtimeNanos()))continue;
                        lastStamp=sample.nanos;ILocalHingeCallback c=callback;if(c!=null)c.angle(sample.nanos,sample.angle);
                    }
                }finally{p.destroy();logs=null;}
            }catch(Exception e){if(!closed&&sampling){status("本地角度暂不可用，正在重试");pause(2000);}}
        }
    }
    private void control(){
        Process lease=null;int failures=0;long retryAt=0;
        try{
            while(!closed){
                boolean active=fresh()&&sampling;
                if(!active){Process p=logs;if(p!=null)p.destroy();}
                if(!active||!dualAllowed){if(lease!=null){stopLease(lease);lease=null;}failures=0;pause(50);continue;}
                try{
                    if(lease!=null&&!lease.isAlive()){stopLease(lease);lease=null;failures++;retryAt=SystemClock.elapsedRealtime()+2000;}
                    if(lease==null&&failures<3&&SystemClock.elapsedRealtime()>=retryAt){
                        String state=command("/system/bin/dumpsys","device_state");
                        if(state.contains("mOverrideState=Optional.empty")&&fresh()&&dualAllowed){
                            ProcessBuilder builder=new ProcessBuilder("/system/bin/app_process","/system/bin","DualScreenLease","0","5");
                            builder.environment().put("CLASSPATH",apk);
                            builder.redirectOutput(ProcessBuilder.Redirect.to(new File("/dev/null"))).redirectError(ProcessBuilder.Redirect.to(new File("/dev/null")));lease=builder.start();
                        }else retryAt=SystemClock.elapsedRealtime()+500;
                    }
                    if(lease!=null){lease.getOutputStream().write('.');lease.getOutputStream().flush();}
                }catch(Exception e){if(lease!=null){stopLease(lease);lease=null;}failures++;retryAt=SystemClock.elapsedRealtime()+2000;status("本地角度已连接，双屏控制正在重试");}
                pause(50);
            }
        }finally{if(lease!=null)stopLease(lease);Process p=logs;if(p!=null)p.destroy();}
    }
    private static void stopLease(Process lease){
        try{lease.getOutputStream().close();if(!lease.waitFor(1000,TimeUnit.MILLISECONDS))lease.destroy();}catch(Exception e){lease.destroy();}
    }
}
