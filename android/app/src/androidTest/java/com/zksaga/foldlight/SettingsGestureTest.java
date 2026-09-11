package com.zksaga.foldlight;

import android.app.*;
import android.content.*;
import android.os.SystemClock;
import android.view.*;
import android.widget.Button;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.*;
import org.junit.runner.RunWith;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

/** Real activity integration: incidental touches cannot reopen settings; a held pair can. */
@RunWith(AndroidJUnit4.class)
public class SettingsGestureTest {
    private Instrumentation instrumentation;
    private MainActivity activity;
    private FoldSurface surface;
    private SettingsPanel settings;
    private SharedPreferences prefs;
    private boolean hadHidden,oldHidden;
    private long down;
    @Before public void start(){
        instrumentation=InstrumentationRegistry.getInstrumentation();
        Context context=instrumentation.getTargetContext();prefs=context.getSharedPreferences("MainActivity",0);
        hadHidden=prefs.contains("hidden");oldHidden=prefs.getBoolean("hidden",false);
        activity=(MainActivity)instrumentation.startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        instrumentation.runOnMainSync(()->{
            View root=activity.getWindow().getDecorView();surface=find(root,FoldSurface.class);settings=find(root,SettingsPanel.class);
            fullscreen(root);
        });
        instrumentation.waitForIdleSync();
    }
    private <T extends View>T find(View view,Class<T> type){
        if(type.isInstance(view))return type.cast(view);
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){T found=find(((ViewGroup)view).getChildAt(i),type);if(found!=null)return found;}
        return null;
    }
    private boolean fullscreen(View view){
        if(view instanceof Button&&((Button)view).getText().toString().equals("全屏体验")){view.performClick();return true;}
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)if(fullscreen(((ViewGroup)view).getChildAt(i)))return true;
        return false;
    }
    private void touch(int action,int count,boolean moved){
        if(action==MotionEvent.ACTION_DOWN)down=SystemClock.uptimeMillis();
        MotionEvent.PointerProperties[] properties=new MotionEvent.PointerProperties[count];
        MotionEvent.PointerCoords[] coords=new MotionEvent.PointerCoords[count];
        for(int i=0;i<count;i++){
            properties[i]=new MotionEvent.PointerProperties();properties[i].id=7+i*4;properties[i].toolType=MotionEvent.TOOL_TYPE_FINGER;
            coords[i]=new MotionEvent.PointerCoords();coords[i].x=300+i*250+(moved&&i==0?220:0);coords[i].y=500;coords[i].pressure=1;coords[i].size=1;
        }
        MotionEvent event=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,count,properties,coords,0,0,1,1,0,0,android.view.InputDevice.SOURCE_TOUCHSCREEN,0);
        instrumentation.runOnMainSync(()->surface.dispatchTouchEvent(event));event.recycle();
    }
    private void assertVisible(boolean visible){AtomicInteger value=new AtomicInteger();instrumentation.runOnMainSync(()->value.set(settings.getVisibility()));assertEquals(visible?View.VISIBLE:View.GONE,value.get());}
    private void pair(){touch(MotionEvent.ACTION_DOWN,1,false);touch(MotionEvent.ACTION_POINTER_DOWN|(1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT),2,false);}
    @Test public void onlyAStationaryHeldPairOpensTheMenu(){
        touch(MotionEvent.ACTION_DOWN,1,false);touch(MotionEvent.ACTION_UP,1,false);SystemClock.sleep(750);assertVisible(false);
        touch(MotionEvent.ACTION_DOWN,1,false);SystemClock.sleep(750);assertVisible(false);touch(MotionEvent.ACTION_UP,1,false);
        pair();touch(MotionEvent.ACTION_POINTER_UP|(1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT),2,false);touch(MotionEvent.ACTION_UP,1,false);SystemClock.sleep(750);assertVisible(false);
        pair();touch(MotionEvent.ACTION_MOVE,2,true);SystemClock.sleep(750);assertVisible(false);touch(MotionEvent.ACTION_CANCEL,2,true);
        pair();touch(MotionEvent.ACTION_POINTER_DOWN|(2<<MotionEvent.ACTION_POINTER_INDEX_SHIFT),3,false);SystemClock.sleep(750);assertVisible(false);touch(MotionEvent.ACTION_CANCEL,3,false);
        pair();SystemClock.sleep(300);assertVisible(false);SystemClock.sleep(450);assertVisible(true);
        touch(MotionEvent.ACTION_POINTER_UP|(1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT),2,false);touch(MotionEvent.ACTION_UP,1,false);
        instrumentation.runOnMainSync(()->fullscreen(activity.getWindow().getDecorView()));SystemClock.sleep(750);assertVisible(false);
        pair();touch(MotionEvent.ACTION_CANCEL,2,false);SystemClock.sleep(750);assertVisible(false);
    }
    private SecondaryPresentation secondary(){
        java.util.concurrent.atomic.AtomicReference<SecondaryPresentation> result=new java.util.concurrent.atomic.AtomicReference<>();
        instrumentation.runOnMainSync(()->{try{java.lang.reflect.Field field=MainActivity.class.getDeclaredField("secondaryPresentation");field.setAccessible(true);result.set((SecondaryPresentation)field.get(activity));}catch(Exception e){throw new AssertionError(e);}});
        return result.get();
    }
    @Test public void menuFitsAfterLiveDualScreenExitAndReentry(){
        org.junit.Assume.assumeTrue("true".equals(InstrumentationRegistry.getArguments().getString("usb_integration"))||"true".equals(InstrumentationRegistry.getArguments().getString("local_integration")));
        for(int pass=0;pass<2;pass++){
            long limit=SystemClock.uptimeMillis()+5000;SecondaryPresentation second;
            while((second=secondary())==null&&SystemClock.uptimeMillis()<limit)SystemClock.sleep(50);
            assertNotNull("continuous local or USB input must activate the second display",second);
            SecondaryPresentation target=second;
            instrumentation.runOnMainSync(()->surface=passIndexSurface(target));
            pair();SystemClock.sleep(750);
            limit=SystemClock.uptimeMillis()+4000;
            while(SystemClock.uptimeMillis()<limit){AtomicInteger ready=new AtomicInteger();instrumentation.runOnMainSync(()->ready.set(settings.getVisibility()==View.VISIBLE&&settings.getAlpha()==1?1:0));if(ready.get()==1)break;SystemClock.sleep(50);}
            instrumentation.runOnMainSync(()->{
                assertEquals(View.VISIBLE,settings.getVisibility());assertEquals(1,settings.getAlpha(),0);assertEquals(0,settings.getTranslationY(),0);
                ViewGroup content=(ViewGroup)settings.getChildAt(0);
                for(int i=0;i<content.getChildCount();i++){View child=content.getChildAt(i);if(child.getVisibility()!=View.GONE)assertTrue("live menu fits new display",child.getRight()<=settings.getWidth()-content.getPaddingRight());}
                surface=find(activity.getWindow().getDecorView(),FoldSurface.class);fullscreen(activity.getWindow().getDecorView());
            });
        }
    }
    @Test public void coverMaximumSliderPersistsAndRestoresItsExactDegrees(){
        boolean existed=prefs.contains("coverMaxAngle");float original=prefs.getFloat("coverMaxAngle",90);
        try {
            pair();SystemClock.sleep(1100);
            instrumentation.runOnMainSync(()->{
                android.widget.SeekBar slider=findCoverMaximum(settings);
                assertNotNull("degree control is present",slider);assertEquals(180,slider.getMax());
                for(int maximum:new int[]{0,120,180}){
                    android.os.Bundle arguments=new android.os.Bundle();
                    arguments.putFloat(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE,maximum);
                    assertTrue(slider.performAccessibilityAction(android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.getId(),arguments));
                    assertEquals(maximum,prefs.getFloat("coverMaxAngle",-1),0);
                }
            });
            instrumentation.runOnMainSync(activity::finish);instrumentation.waitForIdleSync();
            Context context=instrumentation.getTargetContext();
            activity=(MainActivity)instrumentation.startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            instrumentation.runOnMainSync(()->{
                settings=find(activity.getWindow().getDecorView(),SettingsPanel.class);
                assertEquals("setting survives activity restart",180,findCoverMaximum(settings).getProgress());
            });
        } finally {
            if(activity!=null){instrumentation.runOnMainSync(activity::finish);instrumentation.waitForIdleSync();activity=null;}
            SharedPreferences.Editor editor=prefs.edit();if(existed)editor.putFloat("coverMaxAngle",original);else editor.remove("coverMaxAngle");editor.commit();
        }
    }
    @Test public void coverDimmingSwitchPersistsWithoutChangingOtherEffects() throws Exception {
        boolean existed=prefs.contains("coverDimming"),original=prefs.getBoolean("coverDimming",true);
        boolean hadDark=prefs.contains("darkStart"),hadLight=prefs.contains("lightStart");
        float oldDark=prefs.getFloat("darkStart",90),oldLight=prefs.getFloat("lightStart",90);
        boolean hadInnerLight=prefs.contains("innerLightStart"),hadInnerDark=prefs.contains("innerDarkStart");
        float oldInnerLight=prefs.getFloat("innerLightStart",90),oldInnerDark=prefs.getFloat("innerDarkStart",180);
        float maximum=prefs.getFloat("coverMaxAngle",130),strength=prefs.getFloat("innerStrength",1);
        try{
            pair();SystemClock.sleep(1100);
            instrumentation.runOnMainSync(()->{
                android.widget.Switch toggle=find(settings,android.widget.Switch.class);assertNotNull(toggle);
                assertEquals("外屏渐暗",toggle.getText().toString());
                toggle.setChecked(false);assertFalse(prefs.getBoolean("coverDimming",true));
                setProgress(findNamedSlider(settings,"外屏 · 展开开始变暗"),120-40);
                setProgress(findNamedSlider(settings,"外屏 · 合拢开始变亮"),80-41);
                setProgress(findNamedSlider(settings,"内屏左侧 · 展开开始变亮"),60);
                setProgress(findNamedSlider(settings,"内屏左侧 · 合拢开始变暗"),140-1);
                assertEquals(60,prefs.getFloat("innerLightStart",-1),0);assertEquals(140,prefs.getFloat("innerDarkStart",-1),0);
                assertEquals(120,prefs.getFloat("darkStart",-1),0);assertEquals(80,prefs.getFloat("lightStart",-1),0);
            });
            instrumentation.runOnMainSync(activity::finish);instrumentation.waitForIdleSync();
            Context context=instrumentation.getTargetContext();
            activity=(MainActivity)instrumentation.startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            instrumentation.runOnMainSync(()->{
                settings=find(activity.getWindow().getDecorView(),SettingsPanel.class);
                android.widget.Switch toggle=find(settings,android.widget.Switch.class);assertFalse(toggle.isChecked());
                assertEquals(120-40,findNamedSlider(settings,"外屏 · 展开开始变暗").getProgress());
                assertEquals(80-41,findNamedSlider(settings,"外屏 · 合拢开始变亮").getProgress());
                assertEquals(60,findNamedSlider(settings,"内屏左侧 · 展开开始变亮").getProgress());
                assertEquals(139,findNamedSlider(settings,"内屏左侧 · 合拢开始变暗").getProgress());
                toggle.setChecked(true);assertTrue(prefs.getBoolean("coverDimming",false));
                assertEquals(maximum,prefs.getFloat("coverMaxAngle",-1),0);assertEquals(strength,prefs.getFloat("innerStrength",-1),0);
                settings.scrollTo(0,Math.max(0,findNamedSlider(settings,"外屏 · 展开开始变暗").getTop()-180));
            });
            instrumentation.waitForIdleSync();
            android.graphics.Bitmap screenshot=instrumentation.getUiAutomation().takeScreenshot();assertNotNull(screenshot);
            try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(instrumentation.getTargetContext().getCacheDir(),"four-angle-settings.png"))){screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}
        }finally{
            if(activity!=null){instrumentation.runOnMainSync(activity::finish);instrumentation.waitForIdleSync();activity=null;}
            SharedPreferences.Editor editor=prefs.edit();if(existed)editor.putBoolean("coverDimming",original);else editor.remove("coverDimming");
            if(hadDark)editor.putFloat("darkStart",oldDark);else editor.remove("darkStart");
            if(hadLight)editor.putFloat("lightStart",oldLight);else editor.remove("lightStart");
            if(hadInnerLight)editor.putFloat("innerLightStart",oldInnerLight);else editor.remove("innerLightStart");
            if(hadInnerDark)editor.putFloat("innerDarkStart",oldInnerDark);else editor.remove("innerDarkStart");editor.commit();
        }
    }
    @Test public void independentMotionAndGlassSettingsSurviveReopening() throws Exception {
        java.util.Map<String,?> before=new java.util.HashMap<>(prefs.getAll());
        String[] group={"外屏 · 展开","外屏 · 折叠","内屏左侧 · 展开","内屏左侧 · 折叠"};
        String[] fields={"Start","Speed","Amplitude","Acceleration"};
        try{
            pair();SystemClock.sleep(1000);
            instrumentation.runOnMainSync(()->{
                for(int i=0;i<4;i++){
                    setProgress(findNamedSlider(settings,group[i]+"开始变形"),20+i*30);
                    setProgress(findNamedSlider(settings,group[i]+" · 速度"),125+i*25-25);
                    setProgress(findNamedSlider(settings,group[i]+" · 幅度"),60+i*20);
                    setProgress(findNamedSlider(settings,group[i]+" · 加速度"),75+i*25-25);
                }
                setProgress(findNamedSlider(settings,"外屏 · 磨砂强度"),100);
                setProgress(findNamedSlider(settings,"内屏左侧 · 磨砂强度"),0);
                setProgress(findNamedSlider(settings,"外屏 · 磨砂渐变强度"),25);
                setProgress(findNamedSlider(settings,"内屏左侧 · 磨砂渐变强度"),75);
            });
            instrumentation.runOnMainSync(activity::finish);instrumentation.waitForIdleSync();
            activity=(MainActivity)instrumentation.startActivitySync(new Intent(instrumentation.getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            instrumentation.runOnMainSync(()->{
                settings=find(activity.getWindow().getDecorView(),SettingsPanel.class);
                MotionProfile[] profiles=MotionSettings.load(prefs);
                for(int i=0;i<4;i++){
                    assertEquals(20+i*30,profiles[i].start,0);assertEquals(1.25f+i*.25f,profiles[i].speed,.001f);
                    assertEquals(.6f+i*.2f,profiles[i].amplitude,.001f);assertEquals(.75f+i*.25f,profiles[i].acceleration,.001f);
                    assertEquals(20+i*30,findNamedSlider(settings,group[i]+"开始变形").getProgress());
                }
                assertEquals(100,findNamedSlider(settings,"外屏 · 磨砂强度").getMax());
                assertEquals(1,prefs.getFloat("coverFrost",-1),0);assertEquals(0,prefs.getFloat("innerFrost",-1),0);
                assertEquals(.25f,prefs.getFloat("coverGradient",-1),0);assertEquals(.75f,prefs.getFloat("innerGradient",-1),0);
                String[] targets={"外屏 · 展开开始变形","外屏 · 折叠开始变形","内屏左侧 · 展开开始变形","内屏左侧 · 磨砂强度"};
                for(int i=0;i<targets.length;i++){
                    settings.scrollTo(0,Math.max(0,findNamedSlider(settings,targets[i]).getTop()-160));
                    android.graphics.Bitmap shot=android.graphics.Bitmap.createBitmap(settings.getWidth(),settings.getHeight(),android.graphics.Bitmap.Config.ARGB_8888);
                    settings.draw(new android.graphics.Canvas(shot));
                    try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(instrumentation.getTargetContext().getCacheDir(),"motion-settings-"+i+".png"))){shot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}catch(java.io.IOException e){throw new AssertionError(e);}finally{shot.recycle();}
                }

            });
        }finally{
            if(activity!=null){instrumentation.runOnMainSync(activity::finish);instrumentation.waitForIdleSync();activity=null;}
            SharedPreferences.Editor edit=prefs.edit();
            for(String groupKey:MotionProfile.KEYS)for(String field:fields){String key=groupKey+field;if(before.containsKey(key))edit.putFloat(key,(Float)before.get(key));else edit.remove(key);}
            for(String key:new String[]{"coverFrost","innerFrost","coverGradient","innerGradient"}){if(before.containsKey(key))edit.putFloat(key,(Float)before.get(key));else edit.remove(key);}
            edit.commit();
        }
    }
    private void setProgress(android.widget.SeekBar slider,int value){
        assertNotNull(slider);if(slider.getProgress()==value)return;android.os.Bundle args=new android.os.Bundle();
        args.putFloat(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE,value);
        assertTrue(slider.performAccessibilityAction(android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.getId(),args));
    }
    private android.widget.SeekBar findNamedSlider(View view,String name){
        if(view instanceof android.widget.SeekBar&&name.contentEquals(view.getContentDescription()))return (android.widget.SeekBar)view;
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){
            android.widget.SeekBar found=findNamedSlider(((ViewGroup)view).getChildAt(i),name);if(found!=null)return found;
        }return null;
    }
    private android.widget.SeekBar findCoverMaximum(View view){
        if(view instanceof android.widget.SeekBar&&"外屏基准转角".contentEquals(view.getContentDescription()))return (android.widget.SeekBar)view;
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){
            android.widget.SeekBar found=findCoverMaximum(((ViewGroup)view).getChildAt(i));if(found!=null)return found;
        }
        return null;
    }
    @Test public void localBinderReconnectsAfterAppStops(){
        org.junit.Assume.assumeTrue("true".equals(InstrumentationRegistry.getArguments().getString("local_integration")));
        for(int pass=0;pass<3;pass++){
            long limit=SystemClock.uptimeMillis()+6000;boolean connected=false;
            while(SystemClock.uptimeMillis()<limit){
                java.util.concurrent.atomic.AtomicBoolean live=new java.util.concurrent.atomic.AtomicBoolean();
                instrumentation.runOnMainSync(()->{try{
                    java.lang.reflect.Field local=MainActivity.class.getDeclaredField("localSource"),bridge=MainActivity.class.getDeclaredField("bridgeConnected");
                    local.setAccessible(true);bridge.setAccessible(true);live.set(local.getBoolean(activity)&&bridge.getBoolean(activity));
                }catch(Exception e){throw new AssertionError(e);}});
                if(live.get()){connected=true;break;}SystemClock.sleep(50);
            }
            assertTrue("phone Binder connection must work without host bridge",connected);
            if(pass<2){
                instrumentation.runOnMainSync(activity::finish);instrumentation.waitForIdleSync();SystemClock.sleep(1200);
                Context context=instrumentation.getTargetContext();
                activity=(MainActivity)instrumentation.startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            }
        }
    }
    private FoldSurface passIndexSurface(SecondaryPresentation second){return find(second.getWindow().getDecorView(),FoldSurface.class);}
    @After public void finish(){
        if(activity!=null)instrumentation.runOnMainSync(activity::finish);
        if(instrumentation!=null)instrumentation.waitForIdleSync();
        if(prefs!=null){SharedPreferences.Editor editor=prefs.edit();if(hadHidden)editor.putBoolean("hidden",oldHidden);else editor.remove("hidden");editor.commit();}
    }
}
