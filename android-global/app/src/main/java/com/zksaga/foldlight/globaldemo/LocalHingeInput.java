package com.zksaga.foldlight.globaldemo;

import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import rikka.shizuku.Shizuku;

/** Lifecycle-bound Binder client; only an explicit user action requests Shizuku permission. */
final class LocalHingeInput implements AutoCloseable {
    interface Listener {
        void state(boolean local,boolean connected,String message);
        void angle(long timestamp,float degrees);
        boolean sampling();boolean dualAllowed();
    }
    private final Handler main=new Handler(Looper.getMainLooper());
    private final Listener listener;
    private final Shizuku.UserServiceArgs args;
    private ILocalHingeService service;
    private boolean running,closed,binding;
    private long retryAt,lastStamp,bindingSince;
    private String lastMessage="";
    private final Shizuku.OnBinderReceivedListener received=()->main.post(this::connect);
    private final Shizuku.OnBinderDeadListener died=()->main.post(()->{service=null;binding=false;state(false,false,"本地辅助未启动 · 请打开 Shizuku");});
    private final Shizuku.OnRequestPermissionResultListener permission=(code,result)->{
        if(code==26){if(result==PackageManager.PERMISSION_GRANTED)connect();else state(false,false,"未获授权 · 可在 Shizuku 中允许折光·全局实验");}
    };
    private final ILocalHingeCallback callback=new ILocalHingeCallback.Stub(){
        public void angle(long stamp,float value){main.post(()->{if(running&&service!=null&&stamp>lastStamp&&HingeLogParser.fresh(stamp,SystemClock.elapsedRealtimeNanos())&&FoldMath.validAngle(value)){lastStamp=stamp;listener.angle(stamp,value);}});}
        public void status(String message){main.post(()->{if(running&&service!=null)state(true,true,message);});}
    };
    private final ServiceConnection connection=new ServiceConnection(){
        public void onServiceConnected(ComponentName name,IBinder binder){
            if(!running||closed){unbind();return;}
            service=ILocalHingeService.Stub.asInterface(binder);binding=false;
            try{service.start(callback);service.heartbeat(listener.sampling(),listener.dualAllowed());state(true,true,"手机本地辅助已连接");}
            catch(Exception e){lost("本地辅助连接失败 · 请重试");}
        }
        public void onServiceDisconnected(ComponentName name){service=null;binding=false;if(running){retryAt=SystemClock.elapsedRealtime()+1000;state(true,false,"本地辅助正在重连");}}
    };
    private final Runnable tick=new Runnable(){public void run(){
        if(!running||closed)return;
        if(binding&&SystemClock.elapsedRealtime()-bindingSince>5000)lost("本地辅助启动超时 · 正在重试");
        if(service!=null)try{service.heartbeat(listener.sampling(),listener.dualAllowed());}catch(RemoteException e){lost("本地辅助正在重连");}
        else if(!binding&&SystemClock.elapsedRealtime()>=retryAt)connect();
        main.postDelayed(this,50);
    }};
    LocalHingeInput(Context context,Listener listener){
        this.listener=listener;
        args=new Shizuku.UserServiceArgs(new ComponentName(context,LocalHingeService.class)).daemon(false).tag("foldlight-global-hinge").processNameSuffix("hinge").version(BuildConfig.VERSION_CODE);
        Shizuku.addBinderReceivedListenerSticky(received);Shizuku.addBinderDeadListener(died);Shizuku.addRequestPermissionResultListener(permission);
    }
    private void state(boolean local,boolean connected,String message){if(closed)return;String key=local+":"+connected+":"+message;if(!lastMessage.equals(key)){lastMessage=key;listener.state(local,connected,message);}}
    ILocalHingeService bridge(){return service;}
    boolean request(){
        if(!Shizuku.pingBinder()){state(false,false,"本地辅助未启动 · 请打开 Shizuku");return false;}
        try{
            if(Shizuku.checkSelfPermission()==PackageManager.PERMISSION_GRANTED)connect();
            else if(Shizuku.shouldShowRequestPermissionRationale()){state(false,false,"请在 Shizuku → 已授权应用中允许折光·全局实验");return false;}
            else Shizuku.requestPermission(26);
        }catch(RuntimeException e){state(false,false,"Shizuku 不可用 · 请重新启动");return false;}
        return true;
    }
    void resume(){if(closed)return;running=true;main.removeCallbacks(tick);connect();main.post(tick);}
    void pause(){if(service!=null)try{service.heartbeat(false,false);}catch(RemoteException ignored){}}
    void stop(){running=false;main.removeCallbacks(tick);unbind();lastMessage="";}
    private void connect(){
        if(!running||closed||binding||service!=null)return;retryAt=SystemClock.elapsedRealtime()+2000;
        try{
            if(!Shizuku.pingBinder()){state(false,false,"本地辅助未启动 · 请打开 Shizuku");return;}
            if(Shizuku.getVersion()<13||Shizuku.getUid()!=2000){state(false,false,"请使用 Shizuku 13 或更高版本，以调试模式启动");return;}
            if(Shizuku.checkSelfPermission()!=PackageManager.PERMISSION_GRANTED){state(false,false,"启用免 USB 感应 · 需要 Shizuku 授权");return;}
            binding=true;bindingSince=SystemClock.elapsedRealtime();state(true,false,"正在连接手机本地辅助");Shizuku.bindUserService(args,connection);
        }catch(RuntimeException e){binding=false;state(false,false,"本地辅助启动失败 · 请重试");}
    }
    private void lost(String message){unbind();retryAt=SystemClock.elapsedRealtime()+1000;state(true,false,message);}
    private void unbind(){
        if(!binding&&service==null)return;
        if(service!=null)try{service.stop();}catch(RemoteException ignored){}
        service=null;
        try{if(binding||Shizuku.pingBinder())Shizuku.unbindUserService(args,connection,true);}catch(RuntimeException ignored){}
        binding=false;
    }
    @Override public void close(){stop();closed=true;Shizuku.removeBinderReceivedListener(received);Shizuku.removeBinderDeadListener(died);Shizuku.removeRequestPermissionResultListener(permission);main.removeCallbacksAndMessages(null);}
}
