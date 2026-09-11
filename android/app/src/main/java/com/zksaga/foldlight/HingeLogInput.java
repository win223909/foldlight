package com.zksaga.foldlight;

import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import java.io.DataInputStream;
import java.io.IOException;

/** Debug-only receiver. No TCP port, Internet or READ_LOGS permission in the app. */
final class HingeLogInput implements AutoCloseable {
    interface Listener {void state(boolean connected);void angle(long nanos,float angle);boolean dualAllowed();}
    private final Handler main=new Handler(Looper.getMainLooper());
    private final Listener listener;
    private final LocalServerSocket server;
    private volatile LocalSocket client;
    private volatile boolean closed;
    private long lastStamp;
    HingeLogInput(Listener listener) throws IOException {
        this.listener=listener;
        server=new LocalServerSocket("foldlight.hinge."+android.os.Process.myUid());
        Thread worker=new Thread(this::read,"Foldlight angle receiver");worker.setDaemon(true);worker.start();
    }
    private void read(){
        while(!closed){
            try(LocalSocket socket=server.accept()){
                client=socket;
                if(socket.getPeerCredentials().getUid()!=2000)continue;
                socket.setSoTimeout(3000);main.post(()->{if(!closed)listener.state(true);});
                DataInputStream in=new DataInputStream(socket.getInputStream());
                while(!closed){
                    long stamp=in.readLong();float angle=in.readFloat();
                    socket.getOutputStream().write(listener.dualAllowed()?1:0);
                    if(stamp==0)continue; // Heartbeat, never an angle measurement.
                    if(stamp<=lastStamp||!HingeLogParser.fresh(stamp,SystemClock.elapsedRealtimeNanos())||!FoldMath.validAngle(angle))continue;
                    lastStamp=stamp;
                    main.post(()->{if(!closed&&HingeLogParser.fresh(stamp,SystemClock.elapsedRealtimeNanos()))listener.angle(stamp,angle);});
                }
            }catch(IOException e){if(closed)break;}
            finally{client=null;main.post(()->{if(!closed)listener.state(false);});}
        }
    }
    @Override public void close(){closed=true;try{android.system.Os.shutdown(server.getFileDescriptor(),android.system.OsConstants.SHUT_RDWR);}catch(android.system.ErrnoException ignored){}try{server.close();}catch(IOException ignored){}LocalSocket s=client;if(s!=null)try{s.close();}catch(IOException ignored){}main.removeCallbacksAndMessages(null);}
}
