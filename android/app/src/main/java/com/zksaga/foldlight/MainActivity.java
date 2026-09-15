package com.zksaga.foldlight;

import android.app.*;
import android.content.*;
import android.content.res.Configuration;
import android.graphics.*;
import android.hardware.*;
import android.hardware.display.DisplayManager;
import android.net.Uri;
import android.opengl.GLSurfaceView;
import android.os.*;
import android.view.*;
import android.widget.*;
import android.graphics.drawable.GradientDrawable;
import android.util.Log;
import androidx.core.util.Consumer;
import androidx.window.java.layout.WindowInfoTrackerCallbackAdapter;
import androidx.window.layout.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity implements SensorEventListener, Choreographer.FrameCallback {
    private GLSurfaceView surface;
    private FoldRenderer renderer;
    private SecondaryPresentation secondaryPresentation;
    private DisplayManager displays;
    private volatile boolean dualAllowed;
    private final DisplayManager.DisplayListener displayListener=new DisplayManager.DisplayListener(){
        public void onDisplayAdded(int id){refreshCover();}
        public void onDisplayRemoved(int id){refreshCover();}
        public void onDisplayChanged(int id){refreshCover();}
    };
    private FrameLayout root;
    private SettingsPanel panel;
    private View topInsetBackdrop,bottomInsetBackdrop,settingsBackdrop;
    private TextView inputNotice;
    private boolean menuPending;
    private long menuReadyAt,menuDeadline;
    private final Runnable revealMenuTask=this::revealMenu;
    private final Runnable reconnectInputTask=this::startLogInput;
    private SensorManager sensors;
    private Sensor hingeSensor;
    private String vendorState="未发现三星细角度接口";
    private boolean vendorActive;
    private HingeLogInput logInput;
    private final HingeMotion motion=new HingeMotion();
    private final FrameMotion frameMotion=new FrameMotion();
    private boolean bridgeConnected,logActive;
    private float logDelayMs;
    private String bridgeState="角度日志桥未连接";
    private final SensorEventListener vendorProbe=new SensorEventListener(){
        public void onAccuracyChanged(Sensor s,int a){}
        public void onSensorChanged(SensorEvent e){
            if(logActive||e.values.length==0||!FoldMath.validAngle(e.values[0]))return;
            if(!vendorActive){vendorActive=true;sensorTimestamp=0;sensorCount=0;distinct.clear();samples.reset();note("source","Samsung Folding Angle");}
            vendorState="三星细角度接口已收到数据";acceptAngle(e);
        }
    };
    private WindowInfoTrackerCallbackAdapter tracker;
    private final Consumer<WindowLayoutInfo> layoutListener=this::onLayout;
    private final java.util.concurrent.ExecutorService images=Executors.newSingleThreadExecutor();
    private Bitmap coverImage,innerImage;
    private ScreenPictures pictures;
    private Boolean mainCover;
    private boolean coverDimmingEnabled=true,referenceGlass=true;
    private float edgeDeformation=1;
    private final CoverDimming coverDimming=new CoverDimming();
    private final InnerDimming innerDimming=new InnerDimming();
    private float innerLight=1,innerLightStart=EffectDefaults.INNER_LIGHT_START,innerDarkStart=EffectDefaults.INNER_DARK_START;
    private float coverLight=1,darkStart=EffectDefaults.DARK_START,lightStart=EffectDefaults.LIGHT_START;
    private float innerStrength=EffectDefaults.INNER,coverMaxAngle=EffectDefaults.COVER,softness=EffectDefaults.SOFTNESS,frost=EffectDefaults.FROST;
    private MotionProfile[] motionProfiles=MotionProfile.defaults();
    private final DeformationMotion coverDeformation=new DeformationMotion(true),innerDeformation=new DeformationMotion(false);
    private float coverTilt,innerTilt,coverFrost,innerFrost,coverGradient=1,innerGradient=1;
    private LocalHingeInput localInput;
    private boolean localSource;
    private String localMessage="启用免 USB 感应";
    private boolean resumed,hidden,foldPresent,seenLayout,armed,animating,dirty=true,photoBusy,autoCycleStarted,pendingAuto;
    private String posture="等待折叠布局",sensorState="等待角度",mode="sensor";
    private float raw=Float.NaN,displayed=180,manual=120;
    private long sensorTimestamp=0,sensorCount=0,lastFrame=0,animationStart=0,lastStatus=0,lastSample=0,previousDraws=0;
    private long lastSceneChange=0;
    private int previousWidth=0,previousHeight=0,frameCount=0,slowFrames=0;
    private float sensorInterval=0,drawRate=0,animationFrom=90;
    private double maxFrameMs=0;
    private final TreeSet<Integer> distinct=new TreeSet<>();
    private final AngleSamples samples=new AngleSamples();
    private final ArrayDeque<String> records=new ArrayDeque<>();
    private static final int PHOTO_COVER=41,EXPORT=42,PHOTO_INNER=43;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().setDecorFitsSystemWindows(false);
        WindowManager.LayoutParams windowParams=getWindow().getAttributes();
        windowParams.rotationAnimation=WindowManager.LayoutParams.ROTATION_ANIMATION_JUMPCUT;
        getWindow().setAttributes(windowParams);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setNavigationBarContrastEnforced(false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);getWindow().setNavigationBarColor(Color.TRANSPARENT);
        pictures=new ScreenPictures(this);try{pictures.migrate();}catch(IOException e){Log.w("FoldlightProbe","Image migration deferred",e);}
        coverImage=pictures.load(true);innerImage=pictures.load(false);
        SharedPreferences settings=getPreferences(MODE_PRIVATE);EffectDefaults.migrate(settings);
        if(settings.getInt("glassTransitionRevision",0)<1)settings.edit().putFloat("lightStart",70).putInt("glassTransitionRevision",1).apply();
        coverDimmingEnabled=settings.getBoolean("coverDimming",true);
        darkStart=FoldMath.clamp(settings.getFloat("darkStart",EffectDefaults.DARK_START),40,179);lightStart=FoldMath.clamp(settings.getFloat("lightStart",EffectDefaults.LIGHT_START),41,180);
        innerLightStart=FoldMath.clamp(settings.getFloat("innerLightStart",EffectDefaults.INNER_LIGHT_START),0,179);
        innerDarkStart=FoldMath.clamp(settings.getFloat("innerDarkStart",EffectDefaults.INNER_DARK_START),1,180);
        if(saved!=null)displayed=FoldMath.clamp(saved.getFloat("displayedAngle",180),0,180);
        if(saved!=null)innerDimming.restore(saved.getFloat("innerLightState",Float.NaN));
        if(saved!=null)coverDimming.restore(saved.getFloat("coverLightState",Float.NaN));
        // Six-control UI: hidden legacy speed/amplitude profiles must not keep
        // affecting the simplified experience. Retain the user's four angle choices.
        innerStrength=EffectDefaults.INNER;coverMaxAngle=EffectDefaults.COVER;softness=EffectDefaults.SOFTNESS;
        frost=FoldMath.clamp(settings.getFloat("simpleBlur",settings.getFloat("coverFrost",settings.getFloat("frost",EffectDefaults.SIMPLE_BLUR))),0,1);
        edgeDeformation=FoldMath.clamp(settings.getFloat("edgeDeformation",EffectDefaults.EDGE),0,1);
        referenceGlass=true;coverDimmingEnabled=true;motionProfiles=MotionProfile.defaults();
        coverFrost=frost;innerFrost=frost;coverGradient=1;innerGradient=1;
        if(saved!=null&&settings.getInt("uiRevision",0)>=3){coverDeformation.restore(saved.getFloatArray("coverDeformation"));innerDeformation.restore(saved.getFloatArray("innerDeformation"));}
        manual=settings.getFloat("manual",120);mode=settings.getString("mode","sensor");hidden=settings.getBoolean("hidden",false);
        if(settings.getInt("uiRevision",0)<3){hidden=false;settings.edit().putInt("uiRevision",3).putBoolean("hidden",false).apply();}
        if(saved!=null){manual=saved.getFloat("manual",manual);mode=saved.getString("mode",mode);hidden=saved.getBoolean("hidden",hidden);}
        mode="sensor";
        sensors=(SensorManager)getSystemService(SENSOR_SERVICE);hingeSensor=sensors.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE);
        sensorState=hingeSensor==null?"未提供标准铰链角度":"标准铰链传感器 · 等待回调";
        root=new FrameLayout(this);root.setBackgroundColor(Color.rgb(16,27,43));
        surface=new FoldSurface(this);surface.setEGLContextClientVersion(3);surface.setPreserveEGLContextOnPause(true);
        Display initialDisplay=getDisplay();mainCover=initialDisplay!=null&&FoldMath.isCoverSurface(initialDisplay.getMode().getPhysicalWidth(),initialDisplay.getMode().getPhysicalHeight(),Build.MODEL);
        if(saved==null)displayed=mainCover?0:180;renderer=new FoldRenderer(mainCover?coverImage:innerImage);renderer.split=mainCover?0:.5f;renderer.moveRight=mainCover;renderer.displayRotation=initialDisplay==null?0:initialDisplay.getRotation();
        innerLight=innerDimming.step(displayed,innerLightStart,innerDarkStart);
        coverLight=coverDimming.step(displayed,darkStart,lightStart);
        coverTilt=coverDeformation.step(displayed,0,coverMaxAngle,innerStrength,motionProfiles[0],motionProfiles[1]);
        innerTilt=innerDeformation.step(displayed,0,coverMaxAngle,innerStrength,motionProfiles[2],motionProfiles[3]);
        renderer.referenceGlass=referenceGlass;renderer.edgeDeformation=edgeDeformation;renderer.visualTilt=mainCover?coverTilt:innerTilt;renderer.frostGradient=mainCover?coverGradient:innerGradient;
        renderer.innerBrightness=innerLight;renderer.coverBrightness=coverLight;renderer.innerRevealEnabled=true;renderer.coverMaxAngle=mainCover?coverMaxAngle:-1;renderer.strength=innerStrength;renderer.blur=mainCover?coverFrost:innerFrost;
        surface.setRenderer(renderer);surface.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
        ((FoldSurface)surface).setSettingsAction(()->{if(hidden)setHidden(false);});
        surface.getHolder().addCallback(new SurfaceHolder.Callback(){
            public void surfaceCreated(SurfaceHolder holder){holder.getSurface().setFrameRate(120,Surface.FRAME_RATE_COMPATIBILITY_DEFAULT);}
            public void surfaceChanged(SurfaceHolder holder,int format,int w,int h){renderer.displayRotation=getDisplay()==null?0:getDisplay().getRotation();dirty=true;}
            public void surfaceDestroyed(SurfaceHolder holder){}
        });
        root.addView(surface,new FrameLayout.LayoutParams(-1,-1));buildPanel();setContentView(root);
        root.setOnApplyWindowInsetsListener((v,insets)->{Insets safe=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
            panel.safeInsets(safe.top,safe.bottom);
            FrameLayout.LayoutParams noticeBounds=new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.CENTER_HORIZONTAL);
            noticeBounds.topMargin=Math.max(safe.top,dp(12))+dp(12);noticeBounds.leftMargin=dp(16);noticeBounds.rightMargin=dp(16);inputNotice.setLayoutParams(noticeBounds);
            topInsetBackdrop.setLayoutParams(new FrameLayout.LayoutParams(-1,safe.top,Gravity.TOP));
            bottomInsetBackdrop.setLayoutParams(new FrameLayout.LayoutParams(-1,safe.bottom,Gravity.BOTTOM));return insets;});
        root.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{
            int w=r-l,h=b-t;if(w!=previousWidth||h!=previousHeight){
                note("window",w+"x"+h);lastSceneChange=System.nanoTime();
                if(previousWidth>0 && w>previousWidth*1.25f && h>previousHeight*.75f && foldPresent)playFromFold();
                previousWidth=w;previousHeight=h;dirty=true;if(menuPending)menuReadyAt=SystemClock.uptimeMillis()+180;}
        });
        tracker=new WindowInfoTrackerCallbackAdapter(WindowInfoTracker.getOrCreate(this));
        displays=(DisplayManager)getSystemService(DISPLAY_SERVICE);
        localInput=new LocalHingeInput(this,new LocalHingeInput.Listener(){
            public boolean sampling(){return resumed&&mode.equals("sensor");}
            public boolean dualAllowed(){return dualAllowed;}
            public void state(boolean local,boolean connected,String message){
                localMessage=message;panel.localStatus(message);localSource=local;
                if(local){if(logInput!=null){logInput.close();logInput=null;}bridgeConnected=connected;bridgeState=message;if(!connected)logActive=false;}
                else{if(bridgeState.startsWith("手机本地")||bridgeState.startsWith("本地")||logInput==null){bridgeConnected=false;logActive=false;bridgeState=message;}startLogInput();}
                updateInputNotice();dirty=true;note("local_input",message);
            }
            public void angle(long stamp,float angle){if(localSource)receiveContinuousAngle(stamp,angle);}
        });
        setHidden(hidden);note("device",Build.MODEL+" Android "+Build.VERSION.RELEASE+" API "+Build.VERSION.SDK_INT);
    }
    private void startLogInput(){
        if(!resumed||localSource||!BuildConfig.DEBUG||logInput!=null)return;
        try{logInput=new HingeLogInput(new HingeLogInput.Listener(){
            public boolean dualAllowed(){return dualAllowed&&!localSource;}
            public void state(boolean connected){if(localSource)return;bridgeConnected=connected;bridgeState=connected?"角度日志桥已连接":"角度日志桥未连接";if(!connected)logActive=false;note("log_bridge",bridgeState);updateInputNotice();dirty=true;}
            public void angle(long stamp,float angle){if(!localSource)receiveContinuousAngle(stamp,angle);}
        });}catch(IOException e){bridgeState="角度日志桥启动失败";note("log_bridge",e.toString());root.removeCallbacks(reconnectInputTask);root.postDelayed(reconnectInputTask,1000);}
    }
    private void receiveContinuousAngle(long stamp,float angle){
        if(!resumed)return;
        if(!logActive){logActive=true;sensorTimestamp=0;sensorCount=0;distinct.clear();samples.reset();motion.reset();note("source",localSource?"On-device Shizuku angle":"USB angle");}
        logDelayMs=(SystemClock.elapsedRealtimeNanos()-stamp)/1_000_000f;motion.sample(angle,stamp);
        acceptAngle(angle,stamp);updateInputNotice();note("log_delay_ms",String.format(Locale.US,"%.1f",logDelayMs));
    }
    private void enableLocalInput(){
        if(localInput.request())return;
        new AlertDialog.Builder(this).setTitle("启用免 USB 感应")
            .setMessage(localMessage+"\n\n1. 安装并打开 Shizuku。\n2. 通过无线调试启动服务。\n3. 返回折光 Fold，点此按钮并允许授权。\n\n激活后可拔掉 USB。手机重启后需在 Shizuku 中重新启动服务。")
            .setPositiveButton("打开 Shizuku",(d,w)->{
                Intent intent=getPackageManager().getLaunchIntentForPackage("moe.shizuku.privileged.api");
                if(intent==null)intent=new Intent(Intent.ACTION_VIEW,android.net.Uri.parse("https://shizuku.rikka.app/zh-hans/download/"));
                try{startActivity(intent);}catch(android.content.ActivityNotFoundException e){Toast.makeText(this,"请安装 Shizuku 后重试",Toast.LENGTH_LONG).show();}
            }).setNegativeButton("稍后",null).show();
    }
    private void buildPanel(){
        panel=new SettingsPanel(this,coverImage,innerImage,darkStart,lightStart,innerLightStart,innerDarkStart,edgeDeformation,frost,new SettingsPanel.Actions(){
            public void localInput(){enableLocalInput();}
            public void photo(boolean cover){chooseImage(cover);}
            public void fullscreen(){setHidden(true);}
            public void darkStart(float value){darkStart=value;saveMotionSettings();dirty=true;}
            public void lightStart(float value){lightStart=value;saveMotionSettings();dirty=true;}
            public void innerLightStart(float value){innerLightStart=value;saveMotionSettings();dirty=true;}
            public void innerDarkStart(float value){innerDarkStart=value;saveMotionSettings();dirty=true;}
            public void edge(float value){edgeDeformation=value;saveMotionSettings();dirty=true;}
            public void blur(float value){frost=value;coverFrost=value;innerFrost=value;saveMotionSettings();dirty=true;}
            public void defaults(){
                darkStart=EffectDefaults.DARK_START;lightStart=EffectDefaults.LIGHT_START;innerLightStart=EffectDefaults.INNER_LIGHT_START;innerDarkStart=EffectDefaults.INNER_DARK_START;
                edgeDeformation=EffectDefaults.EDGE;frost=EffectDefaults.SIMPLE_BLUR;coverFrost=frost;innerFrost=frost;
                EffectDefaults.restore(getPreferences(MODE_PRIVATE));dirty=true;
                Toast.makeText(MainActivity.this,"已恢复默认推荐参数",Toast.LENGTH_SHORT).show();
            }
            public void diagnostics(){showDiagnostics();}
            public void reset(){new AlertDialog.Builder(MainActivity.this).setTitle("恢复哪一张图片？").setItems(new String[]{"外屏图片","内屏图片"},(d,which)->resetPicture(which==0)).setNegativeButton("取消",null).show();}
        });
        settingsBackdrop=new View(this);settingsBackdrop.setBackgroundColor(0xfff5f5f7);
        root.addView(settingsBackdrop,new FrameLayout.LayoutParams(-1,-1));
        root.addView(panel,new FrameLayout.LayoutParams(-1,-1));
        topInsetBackdrop=new View(this);bottomInsetBackdrop=new View(this);
        topInsetBackdrop.setBackgroundColor(0xfff5f5f7);bottomInsetBackdrop.setBackgroundColor(0xfff5f5f7);
        root.addView(topInsetBackdrop,new FrameLayout.LayoutParams(-1,0,Gravity.TOP));root.addView(bottomInsetBackdrop,new FrameLayout.LayoutParams(-1,0,Gravity.BOTTOM));
        inputNotice=new TextView(this);inputNotice.setTextSize(12);inputNotice.setTextColor(Color.WHITE);inputNotice.setGravity(Gravity.CENTER);inputNotice.setPadding(dp(16),dp(10),dp(16),dp(10));
        GradientDrawable noticeBackground=new GradientDrawable();noticeBackground.setColor(0xee24242a);noticeBackground.setCornerRadius(dp(14));inputNotice.setBackground(noticeBackground);
        root.addView(inputNotice,new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.CENTER_HORIZONTAL));
    }
    private void saveMotionSettings(){getPreferences(MODE_PRIVATE).edit().putFloat("edgeDeformation",edgeDeformation).putFloat("simpleBlur",frost).putFloat("darkStart",darkStart).putFloat("lightStart",lightStart).putFloat("innerLightStart",innerLightStart).putFloat("innerDarkStart",innerDarkStart).apply();}
    private int dp(float x){return Math.round(x*getResources().getDisplayMetrics().density);}
    private void selectMode(String value){mode=value;dualAllowed=resumed&&hidden&&mode.equals("sensor");animating=false;pendingAuto=false;dirty=true;note("mode",mode);if(mode.equals("auto"))play();updateStatus();updateInputNotice();}
    private void updateInputNotice(){
        if(inputNotice==null)return;
        boolean show=hidden&&mode.equals("sensor")&&!logActive&&!vendorActive;
        inputNotice.setText(bridgeConnected?"等待连续角度 · 请轻轻开合屏幕":"实时感应已断开 · 请在设置中启用免 USB 感应");
        inputNotice.setVisibility(show?View.VISIBLE:View.GONE);
    }
    private void revealMenu(){
        if(hidden||!resumed||!menuPending)return;
        long now=SystemClock.uptimeMillis();
        if(now<menuDeadline&&(now<menuReadyAt||(bridgeConnected&&secondaryPresentation!=null))){root.postDelayed(revealMenuTask,50);return;}
        menuPending=false;panel.setReady(true);panel.setTranslationY(0);
        if(android.animation.ValueAnimator.areAnimatorsEnabled())panel.animate().alpha(1).setDuration(120);else panel.setAlpha(1);
    }
    private void setHidden(boolean value){
        hidden=value;dualAllowed=resumed&&hidden&&mode.equals("sensor");panel.animate().cancel();root.removeCallbacks(revealMenuTask);
        topInsetBackdrop.setVisibility(value?View.GONE:View.VISIBLE);bottomInsetBackdrop.setVisibility(value?View.GONE:View.VISIBLE);settingsBackdrop.setVisibility(value?View.GONE:View.VISIBLE);
        panel.setTranslationY(0);
        if(value){menuPending=false;panel.setReady(false);panel.setVisibility(View.GONE);panel.setAlpha(1);}
        else {
            panel.setVisibility(View.VISIBLE);panel.setAlpha(0);panel.setReady(false);panel.requestLayout();
            menuPending=true;menuReadyAt=SystemClock.uptimeMillis()+180;menuDeadline=menuReadyAt+1500;
            root.postDelayed(revealMenuTask,180);updateStatus();
        }
        WindowInsetsController c=getWindow().getInsetsController();if(c!=null){c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);c.setSystemBarsAppearance(value?0:WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);if(value)c.hide(WindowInsets.Type.systemBars());else c.show(WindowInsets.Type.systemBars());}updateInputNotice();dirty=true;
    }
    private void playFromFold(){if(mode.equals("auto")&&!autoCycleStarted){autoCycleStarted=true;armed=false;pendingAuto=true;lastSceneChange=System.nanoTime();dirty=true;note("transition","queued until surface ready");}}
    private void play(){mode="auto";dualAllowed=false;pendingAuto=false;animationStart=System.nanoTime();animationFrom=20;displayed=20;animating=true;dirty=true;note("transition","start");}
    @Override protected void onResume(){super.onResume();resumed=true;surface.onResume();lastFrame=0;frameMotion.reset();dirty=true;
        localInput.resume();startLogInput();setHidden(hidden);dualAllowed=hidden&&mode.equals("sensor");displays.registerDisplayListener(displayListener,new Handler(Looper.getMainLooper()));refreshCover();
        for(Sensor s:sensors.getSensorList(Sensor.TYPE_ALL))if(s.getStringType().equals("com.samsung.sensor.folding_angle")){
            try{boolean ok=sensors.registerListener(vendorProbe,s,SensorManager.SENSOR_DELAY_FASTEST,0);vendorState=ok?"三星细角度接口已订阅 · 等待数据":"三星细角度接口：系统拒绝订阅";note("vendor_register",s.getName()+" result="+ok);}
            catch(SecurityException e){vendorState="三星细角度接口：权限受限";note("vendor_register",e.toString());}
        }
        if(hingeSensor!=null){try{boolean registered=sensors.registerListener(this,hingeSensor,SensorManager.SENSOR_DELAY_FASTEST,0);sensorState=registered?"标准铰链传感器已连接":"传感器注册失败";note("sensor_register",String.valueOf(registered));}catch(SecurityException e){sensorState="系统未允许读取角度";note("sensor_error",e.getClass().getSimpleName());}}
        tracker.addWindowLayoutInfoListener(this,getMainExecutor(),layoutListener);Choreographer.getInstance().postFrameCallback(this);
        note("lifecycle","resume");
    }
    @Override protected void onPause(){dualAllowed=false;if(localInput!=null)localInput.pause();root.removeCallbacks(reconnectInputTask);root.removeCallbacks(revealMenuTask);displays.unregisterDisplayListener(displayListener);closeCover();note("lifecycle","pause");getPreferences(MODE_PRIVATE).edit().putString("mode",mode).putFloat("manual",manual).putBoolean("hidden",hidden).putFloat("innerStrength",innerStrength).putFloat("frost",frost).apply();resumed=false;Choreographer.getInstance().removeFrameCallback(this);sensors.unregisterListener(this);sensors.unregisterListener(vendorProbe);vendorActive=false;tracker.removeWindowLayoutInfoListener(layoutListener);surface.onPause();super.onPause();}
    @Override protected void onStop(){
        if(localInput!=null)localInput.stop();localSource=false;
        if(logInput!=null){logInput.close();logInput=null;}
        bridgeConnected=false;logActive=false;bridgeState="角度日志桥未连接";super.onStop();
    }
    @Override protected void onDestroy(){if(localInput!=null)localInput.close();if(logInput!=null)logInput.close();images.shutdownNow();super.onDestroy();}
    @Override public void onConfigurationChanged(Configuration c){super.onConfigurationChanged(c);dirty=true;note("configuration",c.screenWidthDp+"x"+c.screenHeightDp+" dp");}
    @Override protected void onSaveInstanceState(Bundle b){b.putFloatArray("coverDeformation",coverDeformation.state());b.putFloatArray("innerDeformation",innerDeformation.state());b.putFloat("displayedAngle",displayed);b.putFloat("coverLightState",coverDimming.value());b.putFloat("innerLightState",innerDimming.value());b.putFloat("manual",manual);b.putString("mode",mode);b.putBoolean("hidden",hidden);super.onSaveInstanceState(b);}
    private void onLayout(WindowLayoutInfo info){
        FoldingFeature found=null;for(DisplayFeature f:info.getDisplayFeatures())if(f instanceof FoldingFeature){found=(FoldingFeature)f;break;}
        boolean had=foldPresent;String old=posture;foldPresent=found!=null;
        if(found!=null){posture=found.getState()==FoldingFeature.State.FLAT?"内屏 · 已展平":"内屏 · 半展开";
            // Layout callbacks can arrive before resize: never derive the pivot using old window dimensions.
        }else{posture="当前窗口未报告折痕";}
        if(had&&!foldPresent){autoCycleStarted=false;pendingAuto=false;}
        if(seenLayout&&!had&&foldPresent)playFromFold();
        if(!old.equals(posture))note("posture",posture);seenLayout=true;dirty=true;
    }
    @Override public void onSensorChanged(SensorEvent e){if(logActive||vendorActive||e.sensor.getType()!=Sensor.TYPE_HINGE_ANGLE||e.values.length==0)return;acceptAngle(e);}
    private void acceptAngle(SensorEvent e){
        acceptAngle(e.values[0],e.timestamp);
    }
    private void acceptAngle(float value,long stamp){
        if(!FoldMath.validAngle(value)){note("invalid_angle",String.valueOf(value));return;}
        // Joining or losing a source must not snap the existing visual position.
        if(sensorTimestamp!=0)sensorInterval=(stamp-sensorTimestamp)/1_000_000f;sensorTimestamp=stamp;raw=value;sensorCount++;distinct.add(Math.round(value*10));samples.add(value);
        note("angle",String.format(Locale.US,"%.2f,interval_ms=%.1f",raw,sensorInterval));
        if(raw<=5)autoCycleStarted=false;
        if(raw<120)armed=true;if(raw>=150&&armed&&foldPresent){armed=false;playFromFold();}dirty=true;
    }
    @Override public void onAccuracyChanged(Sensor sensor,int accuracy){}
    @Override public void doFrame(long time){if(!resumed)return;float dt=lastFrame==0?1/60f:(time-lastFrame)/1e9f;
        if(lastFrame!=0){double ms=(time-lastFrame)/1e6;maxFrameMs=Math.max(maxFrameMs,ms);if(ms>25){slowFrames++;note("frame_hitch",String.format(Locale.US,"gap_ms=%.1f raw=%.1f visual=%.1f",ms,raw,displayed));}}lastFrame=time;frameCount++;
        if(pendingAuto&&time-lastSceneChange>200_000_000L&&hasWindowFocus()&&getDisplay()!=null&&getDisplay().getState()==Display.STATE_ON&&renderer.surfaceWidth==root.getWidth()&&renderer.surfaceHeight==root.getHeight())play();
        float target=mode.equals("manual")?manual:mode.equals("sensor")?(logActive?motion.target(SystemClock.elapsedRealtimeNanos()):vendorActive&&!Float.isNaN(raw)?raw:displayed):180;
        if(animating){float p=FoldMath.clamp((time-animationStart)/1_100_000_000f,0,1);float ease=FoldMath.ease(p);target=animationFrom+(180-animationFrom)*ease;if(p>=1){animating=false;note("transition","complete");}displayed=target;frameMotion.reset();}else displayed=frameMotion.step(displayed,target,dt,softness);
        float previousCoverTilt=coverTilt,previousInnerTilt=innerTilt;
        coverTilt=coverDeformation.step(displayed,dt,coverMaxAngle,innerStrength,motionProfiles[0],motionProfiles[1]);
        innerTilt=innerDeformation.step(displayed,dt,coverMaxAngle,innerStrength,motionProfiles[2],motionProfiles[3]);
        if(Math.abs(previousCoverTilt-coverTilt)>.00001f||Math.abs(previousInnerTilt-innerTilt)>.00001f)dirty=true;
        float previousLight=coverLight,previousInnerLight=innerLight;
        float dimmed=coverDimming.step(displayed,darkStart,lightStart);coverLight=coverDimmingEnabled?dimmed:1;
        innerLight=innerDimming.step(displayed,innerLightStart,innerDarkStart);
        if(Math.abs(previousLight-coverLight)>.00001f||Math.abs(previousInnerLight-innerLight)>.00001f)dirty=true;
        boolean cover=FoldMath.isCoverSurface(renderer.surfaceWidth,renderer.surfaceHeight,Build.MODEL);
        float renderAngle=cover?FoldMath.coverHinge(displayed):displayed;
        int rotation=getDisplay()==null?0:getDisplay().getRotation();
        if(renderer.displayRotation!=rotation){renderer.displayRotation=rotation;dirty=true;}
        boolean needsDraw=dirty||animating||Math.abs(renderer.hinge-renderAngle)>.005f;
        renderer.hinge=renderAngle;renderer.splitEnabled=true;
        if(mainCover==null||mainCover!=cover){mainCover=cover;renderer.setImage(cover?coverImage:innerImage);dirty=true;needsDraw=true;}
        renderer.referenceGlass=referenceGlass;renderer.edgeDeformation=edgeDeformation;renderer.visualTilt=cover?coverTilt:innerTilt;renderer.frostGradient=cover?coverGradient:innerGradient;renderer.innerRevealEnabled=true;renderer.innerBrightness=innerLight;renderer.coverBrightness=coverLight;renderer.split=cover?0:.5f;renderer.horizontal=false;renderer.moveRight=cover;renderer.strength=innerStrength;renderer.coverMaxAngle=cover?coverMaxAngle:-1;renderer.blur=cover?coverFrost:innerFrost;
        if(secondaryPresentation!=null){secondaryPresentation.renderer.referenceGlass=referenceGlass;secondaryPresentation.renderer.edgeDeformation=edgeDeformation;secondaryPresentation.renderer.visualTilt=secondaryPresentation.cover?coverTilt:innerTilt;secondaryPresentation.renderer.frostGradient=secondaryPresentation.cover?coverGradient:innerGradient;secondaryPresentation.renderer.coverBrightness=coverLight;secondaryPresentation.renderer.innerBrightness=innerLight;secondaryPresentation.renderer.strength=innerStrength;secondaryPresentation.renderer.coverMaxAngle=secondaryPresentation.cover?coverMaxAngle:-1;secondaryPresentation.renderer.blur=secondaryPresentation.cover?coverFrost:innerFrost;secondaryPresentation.render(displayed,dirty);}
        if(needsDraw){surface.requestRender();dirty=false;}
        if(time-lastStatus>500_000_000L){drawRate=lastStatus==0?0:(renderer.draws-previousDraws)/((time-lastStatus)/1e9f);previousDraws=renderer.draws;lastStatus=time;if(!hidden)updateStatus();}
        if(time-lastSample>1_000_000_000L){lastSample=time;note("frame",String.format(Locale.US,"angle=%.2f,draws=%d,max_callback_ms=%.1f,surface=%dx%d,secondary_draws=%d,secondary_gl=%s,pivot=%.2f",displayed,renderer.draws,maxFrameMs,renderer.surfaceWidth,renderer.surfaceHeight,secondaryPresentation==null?0:secondaryPresentation.renderer.draws,secondaryPresentation==null?"none":secondaryPresentation.renderer.error,renderer.split));}
        Choreographer.getInstance().postFrameCallback(this);
    }
    private void updateStatus(){panel.update(mode,raw,bridgeConnected,logActive,photoBusy);}
    private void closeCover(){if(secondaryPresentation!=null){SecondaryPresentation old=secondaryPresentation;secondaryPresentation=null;old.dismiss();note("cover","dismissed");}}
    private void refreshCover(){
        // Local/USB proof is limited to the measured built-in panels, never an external monitor.
        FoldDeviceProfile profile=FoldDeviceProfile.forModel(Build.MODEL);
        if(!resumed||!hidden||(!BuildConfig.DEBUG&&!localSource)||profile==null){closeCover();return;}
        Display candidate=null;
        // Android excludes rear-facing internal panels from the general presentation category.
        java.util.Map<Integer,Display> panels=new java.util.HashMap<>();
        for(Display d:displays.getDisplays())panels.put(d.getDisplayId(),d);
        for(Display d:displays.getDisplays("android.hardware.display.category.REAR"))panels.put(d.getDisplayId(),d);
        for(Display d:panels.values()){
            Display.Mode m=d.getMode();
            if(profile.isSecondaryPanel(d.getDisplayId(),m.getPhysicalWidth(),m.getPhysicalHeight())&&(d.getFlags()&Display.FLAG_PRESENTATION)!=0){candidate=d;break;}
        }
        if(secondaryPresentation!=null&&(candidate==null||candidate.getDisplayId()!=secondaryPresentation.getDisplay().getDisplayId()||!secondaryPresentation.isShowing()))closeCover();
        if(candidate!=null&&secondaryPresentation==null)try{
            boolean cover=profile.isCover(candidate.getMode().getPhysicalWidth(),candidate.getMode().getPhysicalHeight());
            secondaryPresentation=new SecondaryPresentation(this,candidate,cover?coverImage:innerImage,displayed,cover,coverMaxAngle,innerStrength,frost,()->setHidden(false));
            SecondaryPresentation created=secondaryPresentation;
            created.setOnDismissListener(dialog->{if(secondaryPresentation==created){secondaryPresentation=null;if(resumed)root.post(this::refreshCover);}});
            created.renderer.referenceGlass=referenceGlass;created.renderer.edgeDeformation=edgeDeformation;created.renderer.visualTilt=cover?coverTilt:innerTilt;created.renderer.frostGradient=cover?coverGradient:innerGradient;created.renderer.blur=cover?coverFrost:innerFrost;created.renderer.innerRevealEnabled=true;created.renderer.innerBrightness=innerLight;created.renderer.coverBrightness=coverLight;created.show();
            note("secondary","presentation display="+candidate.getDisplayId()+" cover="+secondaryPresentation.cover);dirty=true;
        }catch(WindowManager.InvalidDisplayException|WindowManager.BadTokenException|SecurityException e){secondaryPresentation=null;note("cover",e.toString());}
    }
    private void setPicture(boolean cover,Bitmap next){if(cover)coverImage=next;else innerImage=next;
        if(mainCover!=null&&mainCover==cover)renderer.setImage(next);
        if(secondaryPresentation!=null&&secondaryPresentation.cover==cover)secondaryPresentation.setImage(next);
        panel.picture(cover,next);dirty=true;
    }
    private void chooseImage(boolean cover){if(photoBusy)return;Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);intent.setType("image/*");intent.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(intent,cover?PHOTO_COVER:PHOTO_INNER);}
    private void resetPicture(boolean cover){if(photoBusy)return;photoBusy=true;updateStatus();images.execute(()->{
        try{Bitmap next=pictures.reset(cover);runOnUiThread(()->{if(isDestroyed())return;setPicture(cover,next);photoBusy=false;updateStatus();});}
        catch(IOException e){runOnUiThread(()->{photoBusy=false;updateStatus();Toast.makeText(this,"恢复失败，请重试",Toast.LENGTH_SHORT).show();});}
    });}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(result!=RESULT_OK||data==null||data.getData()==null)return;Uri uri=data.getData();
        if(request==PHOTO_COVER||request==PHOTO_INNER){final boolean cover=request==PHOTO_COVER;photoBusy=true;updateStatus();images.execute(()->{
            try{Bitmap decoded=pictures.importImage(this,uri,cover);
                runOnUiThread(()->{if(isDestroyed())return;setPicture(cover,decoded);photoBusy=false;updateStatus();Toast.makeText(this,cover?"外屏图片已更新":"内屏图片已更新",Toast.LENGTH_SHORT).show();});
            }catch(Exception|OutOfMemoryError e){runOnUiThread(()->{photoBusy=false;updateStatus();Toast.makeText(this,"图片无法读取，请试试较小的 JPG 或 PNG",Toast.LENGTH_LONG).show();});}
        });}else if(request==EXPORT){String report=report();images.execute(()->{try(OutputStream out=getContentResolver().openOutputStream(uri)){if(out==null)throw new IOException();out.write(report.getBytes(java.nio.charset.StandardCharsets.UTF_8));runOnUiThread(()->Toast.makeText(this,"诊断已导出",Toast.LENGTH_SHORT).show());}catch(Exception e){runOnUiThread(()->Toast.makeText(this,"导出失败，请重试",Toast.LENGTH_SHORT).show());}});}
    }
    private void showDiagnostics(){new AlertDialog.Builder(this).setTitle("折叠诊断").setMessage(Build.MODEL+" · Android "+Build.VERSION.RELEASE+"\n"+sensorState+"\n"+vendorState+"\n"+bridgeState+"\n"+samples.description()+"\n角度事件："+sensorCount+"，不同角度："+distinct.size()+"\n最近回调间隔："+Math.round(sensorInterval)+" ms\n显示刷新率："+(getDisplay()==null?"—":getDisplay().getRefreshRate())+" Hz\n"+posture+"\n\n诊断只包含型号、角度和绘制时间，不记录屏幕内容或图片。静止时传感器可能不发送事件。系统级传感器权限无法通过普通授权弹窗获得。")
        .setPositiveButton("导出诊断",(d,w)->{Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.setType("text/plain");i.putExtra(Intent.EXTRA_TITLE,"Foldlight-diagnostics.txt");startActivityForResult(i,EXPORT);})
        .setNegativeButton("关闭",null).show();}
    private void note(String kind,String value){String line=SystemClock.elapsedRealtime()+"\t"+kind+"\t"+value;records.add(line);while(records.size()>2400)records.removeFirst();if(!kind.equals("frame"))Log.i("FoldlightProbe",line);
        if(kind.equals("frame")&&!images.isShutdown()){String snapshot=report();images.execute(()->{try(FileOutputStream out=new FileOutputStream(new File(getFilesDir(),"diagnostic-latest.txt"))){out.write(snapshot.getBytes(java.nio.charset.StandardCharsets.UTF_8));}catch(IOException ignored){}});}
    }
    private String report(){return "Foldlight "+BuildConfig.VERSION_NAME+"\n"+Build.MODEL+" Android "+Build.VERSION.RELEASE+" API "+Build.VERSION.SDK_INT+"\nreferenceGlass="+referenceGlass+"\nmode="+mode+"\nsensor="+(hingeSensor==null?"none":hingeSensor.getName())+"\nlogBridge="+bridgeState+" active="+logActive+" delayMs="+logDelayMs+"\nvendor="+vendorState+" active="+vendorActive+"\n"+samples.description()+"\nraw="+raw+" distinct="+distinct.size()+" events="+sensorCount+"\ncallbacks="+frameCount+" over25ms="+slowFrames+" maxCallbackMs="+maxFrameMs+"\nGL="+renderer.error+"\n"+String.join("\n",records)+"\n";}
}
