package com.zksaga.foldlight.globaldemo;

import android.accessibilityservice.AccessibilityService;
import android.app.KeyguardManager;
import android.content.*;
import android.graphics.*;
import android.hardware.display.DisplayManager;
import android.os.*;
import android.util.Log;
import android.view.*;
import android.view.accessibility.AccessibilityEvent;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;

/** Stable physical mapping, covered task transfer, then a verified-frame handover. */
public final class GlobalEffectService extends AccessibilityService implements Choreographer.FrameCallback {
    static volatile GlobalEffectService instance;
    static volatile String status="等待开启无障碍服务";
    static volatile float measured=Float.NaN;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService jobs=Executors.newSingleThreadExecutor();
    private final List<FoldLayer> layers=new ArrayList<>();
    private final Map<Integer,Bitmap> frames=new HashMap<>();
    private SharedPreferences effectPrefs;
    private EffectParameters parameters=EffectParameters.read(Collections.emptyMap());
    private final SharedPreferences.OnSharedPreferenceChangeListener effectChanged=(prefs,key)->{
        parameters=EffectParameters.read(prefs.getAll());for(FoldLayer layer:layers)layer.parameters(parameters);schedule();
    };
    private LocalHingeInput input;private DisplayManager displays;
    private volatile boolean enabled,closed;private volatile int generation,taskDisplay;
    private boolean dualReady,polling,busy,finishing,framing;private long serial,retryAfter,lastAngle,lastFrame,sessionStart;
    private float angle=0,visual=0;private TransitionPolicy policy=new TransitionPolicy(false);
    private final DisplayContinuity continuity=new DisplayContinuity();
    private long frameCount,slowFrames,maxFrameGap;
    private final BroadcastReceiver power=new BroadcastReceiver(){public void onReceive(Context c,Intent i){if(!available())release("已锁屏 · 解锁后合拢一次恢复");}};
    private void trace(String s){Log.i("FoldlightStable",s);}
    @Override public void onServiceConnected(){
        instance=this;effectPrefs=getSharedPreferences(EffectParameters.STORE,0);parameters=EffectParameters.read(effectPrefs.getAll());effectPrefs.registerOnSharedPreferenceChangeListener(effectChanged);displays=getSystemService(DisplayManager.class);
        IntentFilter f=new IntentFilter(Intent.ACTION_SCREEN_OFF);f.addAction(Intent.ACTION_USER_PRESENT);registerReceiver(power,f,Context.RECEIVER_NOT_EXPORTED);
        input=new LocalHingeInput(this,new LocalHingeInput.Listener(){
            public void state(boolean local,boolean connected,String message){if(!connected){status=message;release(message);}}
            public void angle(long stamp,float value){accept(value);}
            public boolean sampling(){return enabled&&available();}
            public boolean dualAllowed(){return enabled&&available()&&!MainActivity.foreground&&SystemClock.uptimeMillis()>=retryAfter;}
        });
        enabled=getSharedPreferences("global",0).getBoolean("enabled",false);if(enabled)input.resume();main.post(health);
    }
    boolean available(){return getSystemService(PowerManager.class).isInteractive()&&!getSystemService(KeyguardManager.class).isKeyguardLocked();}
    void setEnabled(boolean next){enabled=next;getSharedPreferences("global",0).edit().putBoolean("enabled",next).apply();release(next?"请合拢一次，然后在浏览器中展开":"已暂停 · 正常切屏已恢复");if(next)input.resume();else input.stop();}
    void previewHome(){Toast.makeText(this,"这一版请在浏览器等普通 App 中开合测试；暂不接管桌面",Toast.LENGTH_LONG).show();}
    private final Runnable health=new Runnable(){public void run(){
        if(closed)return;
        if(enabled){
            if(!available()||MainActivity.foreground){if(dualReady||!layers.isEmpty())release("离开设置并合拢一次，准备双屏");}
            else if(input.bridge()!=null&&!polling&&SystemClock.uptimeMillis()>=retryAfter){
                polling=true;ILocalHingeService bridge=input.bridge();int ticket=generation;
                jobs.execute(()->{boolean ready=false,baseClosed=false;try{Bundle state=bridge.sessionState();ready=state.getBoolean("ready");baseClosed=state.getBoolean("physicallyClosed");}catch(Exception ignored){}final boolean result=ready,physicalClosed=baseClosed;main.post(()->{
                    polling=false;if(closed||ticket!=generation||SystemClock.uptimeMillis()<retryAfter)return;
                    // The physical CLOSED state confirms the endpoint even when the
                    // HAL stops just above zero during the magnetic latch handover.
                    if(physicalClosed&&dualReady&&policy.active&&!policy.opening&&taskDisplay==0){
                        if(angle!=0)trace("physical closed confirms endpoint; lastAngle="+angle);
                        accept(0);
                    }
                    boolean recovering=continuity.waiting();
                    if(!continuity.retain(result,dualReady&&taskDisplay==0&&!policy.opening,SystemClock.uptimeMillis())){
                        if(dualReady){fail("双屏未恢复，请合拢后重试");return;}
                    }
                    if(!result&&continuity.waiting()){if(!recovering)trace("closed-panel gap: retain cover layer and task");return;}
                    if(result&&recovering){trace("closed-panel gap recovered without resetting task");accept(angle);}
                    if(result&&!dualReady){dualReady=true;taskDisplay=0;policy=new TransitionPolicy(false);trace("stable panels ready cover=0 inner=1");status="双屏已就绪 · 在普通 App 中开合体验";}
                    else if(!result){if(dualReady){fail("双屏暂不可用，请合拢后重试");return;}if(layers.isEmpty())status="请完全合拢一次，准备双屏";}
                });});
            }
            if(!layers.isEmpty()&&SystemClock.uptimeMillis()-sessionStart>18000)fail("本次交接超时，已恢复原页面");
            // HAL may stop emitting once the hinge is stationary. Complete the
            // endpoint hold on the health clock, using only a recent real sample.
            long now=SystemClock.uptimeMillis();
            if(dualReady&&policy.active&&now-lastAngle<1500&&policy.update(angle,now)==2)finish();
        }
        main.postDelayed(this,150);
    }};
    private void accept(float value){
        if(!enabled||closed||!Float.isFinite(value)||value<0||value>180)return;
        angle=measured=value;lastAngle=SystemClock.uptimeMillis();
        if(!dualReady||MainActivity.foreground||!available())return;
        if(continuity.waiting()){schedule();return;}
        int change=policy.update(value,lastAngle);
        if(change==1||change==-1)transition(change==1);
        else if(change==2)finish();
        schedule();
    }
    private Display display(int id){Display d=displays.getDisplay(id);if(d==null||d.getRotation()!=Surface.ROTATION_0)throw new IllegalStateException("请保持手机竖直方向");return d;}
    private boolean current(int ticket){return ticket==generation&&enabled&&!closed&&available()&&!MainActivity.foreground;}
    private SurfaceControl[] excludes(){return layers.stream().map(FoldLayer::getSurfaceControl).filter(s->s!=null&&s.isValid()).toArray(SurfaceControl[]::new);}
    private Bitmap capture(ILocalHingeService b,int id,SurfaceControl[] excluded)throws Exception{
        Bundle r=b.captureBehind(id,excluded);Bitmap hw=r.getParcelable("frame");if(hw==null)throw new IllegalStateException(r.getString("error","无法获取页面"));
        Bitmap cpu=hw.copy(Bitmap.Config.ARGB_8888,false);hw.recycle();if(cpu==null)throw new IllegalStateException("截图不可用");
        Display.Mode m=display(id).getMode();if(cpu.getWidth()!=m.getPhysicalWidth()||cpu.getHeight()!=m.getPhysicalHeight()){cpu.recycle();throw new IllegalStateException("页面正在调整尺寸");}return cpu;
    }
    private void transition(boolean opening){
        int ticket=++generation;busy=true;finishing=false;sessionStart=SystemClock.uptimeMillis();frameCount=slowFrames=maxFrameGap=0;
        for(FoldLayer l:new ArrayList<>(layers)){
            if(!l.ready){layers.remove(l);l.close();continue;}
            l.animate().cancel();l.setAlpha(1);
        }
        ILocalHingeService bridge=input.bridge();SurfaceControl[] excluded=excludes();int destination=opening?1:0;
        trace("transition destination="+destination+" angle="+angle);
        jobs.execute(()->{try{
            if(!current(ticket)||bridge==null)return;int source=taskDisplay;
            Bitmap bitmap=capture(bridge,source,excluded);
            main.post(()->{
                if(!current(ticket)){bitmap.recycle();return;}
                replaceFrame(source,bitmap);
                addLayer(source,bitmap,ticket,()->{
                    if(source==destination){busy=false;if(!policy.active)finish();return;}
                    // Cover the receiving panel before requesting any app resize.
                    Bitmap previous=frames.get(destination);
                    addLayer(destination,previous!=null?previous:bitmap,ticket,()->moveCovered(ticket,source,destination,bridge));
                });
            });
        }catch(Exception e){main.post(()->{if(current(ticket))fail("交接未完成："+rootMessage(e));});}});
    }
    private void moveCovered(int ticket,int source,int destination,ILocalHingeService bridge){
        SurfaceControl[] excluded=excludes();trace("both surfaces committed, moving task");
        jobs.execute(()->{try{
            if(!current(ticket))return;Bundle moved=bridge.moveTask(source,destination);if(!moved.getBoolean("ok"))throw new IllegalStateException(moved.getString("error","App 暂不支持切屏"));
            taskDisplay=destination;
            if(!awaitWindow(bridge,destination,ticket))return;
            Bitmap bitmap=capture(bridge,destination,excluded);
            main.post(()->{if(!current(ticket)){bitmap.recycle();return;}replaceFrame(destination,bitmap);
                addLayer(destination,bitmap,ticket,()->{busy=false;status="正在跟随开合 · 当前 App 已提前适配目标屏幕";trace("native layout committed display="+destination+" angle="+angle);if(!policy.active)finish();});
            });
        }catch(Exception e){main.post(()->{if(current(ticket))fail("交接未完成："+rootMessage(e));});}});
    }
    private boolean awaitWindow(ILocalHingeService b,int id,int ticket)throws Exception{
        long deadline=SystemClock.uptimeMillis()+2300;String last="";int matches=0;
        while(current(ticket)&&SystemClock.uptimeMillis()<deadline){
            Bundle state=b.windowState(id);String geometry=state.getString("geometry","");
            matches=state.getBoolean("ready")&&!geometry.isEmpty()?(geometry.equals(last)?matches+1:1):0;last=geometry;
            if(matches>=2)return true;Thread.sleep(40);
        }
        if(current(ticket))throw new IllegalStateException("目标页面尚未稳定");return false;
    }
    private void replaceFrame(int id,Bitmap next){Bitmap previous=frames.put(id,next);if(previous!=null&&previous!=next)previous.recycle();}
    private void addLayer(int id,Bitmap bitmap,int ticket,Runnable done){
        if(!current(ticket))return;
        try{
            FoldLayer[] box=new FoldLayer[1];long number=++serial;
            box[0]=new FoldLayer(this,display(id),bitmap,id==1,number,visual,parameters,()->{
                FoldLayer layer=box[0];if(!current(ticket)||!layers.contains(layer))return;
                for(FoldLayer old:new ArrayList<>(layers))if(old.displayId==id&&old.serial<number){layers.remove(old);old.close();}
                trace("layer committed display="+id);done.run();schedule();
            });
            layers.add(box[0]);box[0].attach();schedule();
            main.postDelayed(()->{if(current(ticket)&&layers.contains(box[0])&&!box[0].ready)fail("效果层未完成显示，已恢复页面");},1800);
        }catch(Exception e){fail("效果层不可用："+rootMessage(e));}
    }
    private void finish(){
        if(busy||finishing||layers.isEmpty())return;finishing=true;int ticket=generation,id=taskDisplay;ILocalHingeService bridge=input.bridge();
        jobs.execute(()->{try{
            if(!current(ticket)||bridge==null||!awaitWindow(bridge,id,ticket))return;
            main.post(()->{if(!current(ticket))return;trace("stable endpoint reveal display="+id+" frames="+frameCount+" gapsOver33ms="+slowFrames+" maxGapMs="+(maxFrameGap/1000000));
                for(FoldLayer l:layers)l.animate().alpha(0).setDuration(180).start();
                main.postDelayed(()->{if(current(ticket)){clearLayers();clearFrames();busy=finishing=false;visual=angle;status="双屏保持连接 · 可继续开合";}},210);
            });
        }catch(Exception e){main.post(()->{if(current(ticket))fail("结束交接失败："+rootMessage(e));});}});
    }
    private void schedule(){if(!framing&&!closed&&!layers.isEmpty()){framing=true;Choreographer.getInstance().postFrameCallback(this);}}
    @Override public void doFrame(long now){framing=false;if(closed||layers.isEmpty())return;
        // Lock/screen broadcasts, incoming angles and the 150 ms health check
        // enforce availability. Avoid synchronous system IPC on every display frame.
        if(MainActivity.foreground){release("已恢复正常切屏");return;}
        if(lastFrame!=0){long gap=now-lastFrame;maxFrameGap=Math.max(maxFrameGap,gap);if(gap>33000000)slowFrames++;}frameCount++;
        float dt=lastFrame==0?1/60f:Math.min(.05f,(now-lastFrame)/1e9f);lastFrame=now;
        visual=angle+(visual-angle)*(float)Math.exp(-dt/.035f);
        for(FoldLayer l:layers){if(!l.healthy()){fail("图形层异常，已恢复页面");return;}l.step(visual);}
        schedule();
    }
    private void fail(String why){trace(why);retryAfter=SystemClock.uptimeMillis()+3000;release(why);}
    private void release(String why){++generation;continuity.reset();busy=finishing=dualReady=false;policy=new TransitionPolicy(false);clearLayers();clearFrames();if(input!=null)input.pause();status=why;}
    private void clearLayers(){for(FoldLayer l:layers)l.close();layers.clear();Choreographer.getInstance().removeFrameCallback(this);framing=false;lastFrame=0;}
    private void clearFrames(){for(Bitmap b:frames.values())b.recycle();frames.clear();}
    private static String rootMessage(Throwable e){while(e.getCause()!=null)e=e.getCause();return String.valueOf(e.getMessage());}
    @Override public void onAccessibilityEvent(AccessibilityEvent e){}
    @Override public void onInterrupt(){release("系统中断，已释放双屏");}
    @Override public void onDestroy(){if(effectPrefs!=null)effectPrefs.unregisterOnSharedPreferenceChangeListener(effectChanged);closed=true;enabled=false;release("实验服务已关闭");if(input!=null)input.close();main.removeCallbacksAndMessages(null);jobs.shutdown();try{unregisterReceiver(power);}catch(Exception ignored){}instance=null;super.onDestroy();}
}
