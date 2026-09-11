package com.zksaga.foldlight;

import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.os.SystemClock;
import java.io.*;
import java.util.concurrent.atomic.AtomicReference;

/** Explicit ADB launch only. Reads the sensor HAL tag; sends only timestamp + angle on-device. */
public final class HingeLogPump {
    public static void main(String[] args) throws Exception {
        if(android.os.Process.myUid()!=2000||args.length!=2)throw new IllegalArgumentException("ADB shell, app UID and sensor HAL PID required");
        int appUid=Integer.parseInt(args[0]),halPid=Integer.parseInt(args[1]);
        if(appUid<10000||halPid<1)throw new IllegalArgumentException("invalid UID/PID");
        Process logs=new ProcessBuilder("/system/bin/logcat","-b","main","--pid="+halPid,"--uid=1000","-v","brief","-T","1","-s","sensors-hal:I","*:S").redirectError(ProcessBuilder.Redirect.INHERIT).start();
        AtomicReference<HingeLogParser.Sample> latest=new AtomicReference<>();
        Thread reader=new Thread(()->{
            try(BufferedReader in=new BufferedReader(new InputStreamReader(logs.getInputStream()))){
                String line;while((line=in.readLine())!=null){HingeLogParser.Sample sample=HingeLogParser.parse(line);
                    if(sample!=null&&HingeLogParser.fresh(sample.nanos,SystemClock.elapsedRealtimeNanos())){
                        HingeLogParser.Sample old=latest.get();if(old==null||sample.nanos>old.nanos)latest.set(sample);
                    }
                }
            }catch(IOException ignored){}
        },"Foldlight HAL log reader");reader.setDaemon(true);reader.start();
        Runtime.getRuntime().addShutdownHook(new Thread(logs::destroy));
        long deadline=SystemClock.elapsedRealtime()+600_000L,sent=0;int retries=0;
        System.out.println("Angle bridge started; 10 minute limit; only current sensor angle forwarded.");
        try{
            while(SystemClock.elapsedRealtime()<deadline&&reader.isAlive()){
                try(LocalSocket socket=new LocalSocket()){
                    socket.connect(new LocalSocketAddress("foldlight.hinge."+appUid));
                    if(socket.getPeerCredentials().getUid()!=appUid)throw new IOException("Unexpected receiver UID");
                    DataOutputStream out=new DataOutputStream(socket.getOutputStream());long stamp=0,heartbeat=0;
                    System.out.println("App connected");
                    while(SystemClock.elapsedRealtime()<deadline&&reader.isAlive()){
                        HingeLogParser.Sample sample=latest.get();long now=SystemClock.elapsedRealtime();
                        if(sample!=null&&sample.nanos>stamp&&HingeLogParser.fresh(sample.nanos,SystemClock.elapsedRealtimeNanos())){
                            out.writeLong(sample.nanos);out.writeFloat(sample.angle);out.flush();stamp=sample.nanos;sent++;
                        }else if(now-heartbeat>1000){out.writeLong(0);out.writeFloat(0);out.flush();heartbeat=now;}
                        Thread.sleep(10);
                    }
                }catch(IOException e){if(retries++%20==0)System.out.println("Waiting for app: "+e);Thread.sleep(500);}
            }
        }finally{logs.destroy();System.out.println("Angle bridge stopped; samples="+sent);}
    }
}
