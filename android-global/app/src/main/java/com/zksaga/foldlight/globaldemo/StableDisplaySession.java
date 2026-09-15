package com.zksaga.foldlight.globaldemo;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.os.*;
import android.view.Display;
import android.app.ActivityOptions;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.Executor;

/** Keep physical cover on logical 0; migrate existing tasks, never swap a lit primary. */
final class StableDisplaySession {
    private final Context context;
    private Object manager,owned,atm,wm;private Class<?> taskApi;
    private long retryAt;
    private final Set<Integer> moved=new HashSet<>();
    StableDisplaySession(Context context){this.context=context;}
    private Object service(String name,String type)throws Exception{
        IBinder b=(IBinder)Class.forName("android.os.ServiceManager").getMethod("getService",String.class).invoke(null,name);
        return Class.forName(type+"$Stub").getMethod("asInterface",IBinder.class).invoke(null,b);
    }
    boolean unlocked()throws Exception{
        if(wm==null)wm=service("window","android.view.IWindowManager");
        return context.getSystemService(PowerManager.class).isInteractive()&&!(boolean)Class.forName("android.view.IWindowManager").getMethod("isKeyguardLocked").invoke(wm);
    }
    private Display panel(int id){return context.getSystemService(DisplayManager.class).getDisplay(id);}
    private boolean coverPrimary(){Display p=panel(0);return p!=null&&p.getState()==Display.STATE_ON&&p.getMode().getPhysicalWidth()==1248;}
    @android.annotation.SuppressLint("WrongConstant") // Shell-only hidden service, verified on SM-F9710.
    synchronized void update(boolean wanted){
        if(!wanted){close();return;}
        try{
            if(!Build.MODEL.equals("SM-F9710")||!unlocked()){close();return;}
            if(owned!=null)return;
            if(SystemClock.elapsedRealtime()<retryAt||!coverPrimary())return;
            retryAt=SystemClock.elapsedRealtime()+1000;
            // Resolve advertised state and reject other owners, including original Foldlight.
            Object ds=service("device_state","android.hardware.devicestate.IDeviceStateManager");
            Object info=Class.forName("android.hardware.devicestate.IDeviceStateManager").getMethod("getDeviceStateInfo").invoke(ds);
            Object cur=info.getClass().getField("currentState").get(info),base=info.getClass().getField("baseState").get(info);
            int curId=(int)cur.getClass().getMethod("getIdentifier").invoke(cur),baseId=(int)base.getClass().getMethod("getIdentifier").invoke(base);
            if(curId!=baseId)return;
            manager=context.getSystemService("device_state");int target=-1;
            for(Object state:(List<?>)manager.getClass().getMethod("getSupportedDeviceStates").invoke(manager)){
                if("CONCURRENT_OUTER_DEFAULT".equals(state.getClass().getMethod("getName").invoke(state)))target=(int)state.getClass().getMethod("getIdentifier").invoke(state);
            }
            if(target<0)throw new IllegalStateException("No concurrent outer state");
            Class<?> type=Class.forName("android.hardware.devicestate.DeviceStateRequest"),cb=Class.forName("android.hardware.devicestate.DeviceStateRequest$Callback");
            Object b=type.getMethod("newBuilder",int.class).invoke(null,target),req=b.getClass().getMethod("build").invoke(b);
            Object callback=Proxy.newProxyInstance(cb.getClassLoader(),new Class[]{cb},(p,m,a)->{
                if(m.getName().equals("hashCode"))return System.identityHashCode(p);
                if(m.getName().equals("equals"))return p==a[0];
                if(m.getName().equals("onRequestCanceled"))synchronized(this){if(owned==req){owned=null;android.util.Log.i("FoldlightStable","system canceled display request; preserving moved tasks");}}
                return null;
            });
            owned=req;
            try{manager.getClass().getMethod("requestState",type,Executor.class,cb).invoke(manager,req,(Executor)Runnable::run,callback);}
            catch(Exception e){owned=null;throw e;}
            android.util.Log.i("FoldlightStable","cover-primary request acquired");
        }catch(Exception e){android.util.Log.w("FoldlightStable","display request failed: "+e.getClass().getSimpleName());close();}
    }
    boolean physicallyClosed(){
        try{
            Object ds=service("device_state","android.hardware.devicestate.IDeviceStateManager");
            Object info=Class.forName("android.hardware.devicestate.IDeviceStateManager").getMethod("getDeviceStateInfo").invoke(ds);
            Object base=info.getClass().getField("baseState").get(info);
            return "CLOSED".equals(base.getClass().getMethod("getName").invoke(base));
        }catch(Exception unavailable){return false;}
    }
    synchronized boolean ready(){return owned!=null&&coverPrimary()&&panel(1)!=null&&panel(1).getState()==Display.STATE_ON&&panel(1).getMode().getPhysicalWidth()==2448;}
    private void taskManager()throws Exception{if(atm==null){taskApi=Class.forName("android.app.IActivityTaskManager");atm=Class.forName("android.app.ActivityTaskManager").getMethod("getService").invoke(null);}}
    private List<?> tasks(int display)throws Exception{taskManager();return (List<?>)taskApi.getMethod("getTasks",int.class,boolean.class,boolean.class,int.class).invoke(atm,16,false,false,display);}
    private int number(Object task,String field)throws Exception{return (int)task.getClass().getField(field).get(task);}
    private boolean regular(Object task)throws Exception{
        Object c=task.getClass().getField("configuration").get(task),w=c.getClass().getField("windowConfiguration").get(c);
        return (int)w.getClass().getMethod("getActivityType").invoke(w)==1;
    }
    synchronized Bundle move(int from,int to)throws Exception{
        if(!ready()||!unlocked()||from==to||from<0||from>1||to<0||to>1)throw new IllegalStateException("Display session unavailable");
        List<?> list=tasks(from);if(list.isEmpty()||!regular(list.get(0)))throw new IllegalStateException("请在浏览器等普通 App 中体验，暂不支持桌面和最近任务");
        int id=number(list.get(0),"taskId");
        int result=(int)taskApi.getMethod("startActivityFromRecents",int.class,Bundle.class).invoke(atm,id,ActivityOptions.makeBasic().setLaunchDisplayId(to).toBundle());
        if(result<0)throw new IllegalStateException("App refused migration");
        moved.add(id);Bundle b=new Bundle();b.putBoolean("ok",true);b.putInt("task",id);b.putInt("display",to);
        android.util.Log.i("FoldlightStable","task="+id+" migrated "+from+"->"+to);return b;
    }
    synchronized void back()throws Exception{
        if(!ready()||!unlocked())return;
        java.lang.Process p=new ProcessBuilder("input","-d","1","keyevent","4").start();
        try{if(!p.waitFor(1000,java.util.concurrent.TimeUnit.MILLISECONDS))p.destroy();}finally{p.destroy();}
    }
    synchronized void close(){
        if(owned==null&&moved.isEmpty())return;
        try{
            // Restore only tasks this session moved; do not operate unrelated applications.
            List<?> inner=new ArrayList<>(tasks(1));Collections.reverse(inner);
            for(Object t:inner)if(moved.contains(number(t,"taskId"))&&regular(t))taskApi.getMethod("startActivityFromRecents",int.class,Bundle.class).invoke(atm,number(t,"taskId"),ActivityOptions.makeBasic().setLaunchDisplayId(0).toBundle());
        }catch(Exception e){android.util.Log.w("FoldlightStable","task restore failed: "+e.getClass().getSimpleName());}
        moved.clear();
        if(owned!=null){owned=null;try{manager.getClass().getMethod("cancelStateRequest").invoke(manager);}catch(Exception ignored){}}
        android.util.Log.i("FoldlightStable","display session released");
    }
}
