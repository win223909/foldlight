package com.zksaga.foldlight.globaldemo;

import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import rikka.shizuku.Shizuku;

/** Separate opt-in experiment; no reads or writes to the original Foldlight package. */
public final class MainActivity extends Activity {
    static volatile boolean foreground;
    private final Handler main=new Handler(Looper.getMainLooper());
    private TextView status,angle;
    private Button toggle;
    private final Runnable refresh=new Runnable(){public void run(){
        status.setText(GlobalEffectService.status);
        float a=GlobalEffectService.measured;angle.setText(Float.isNaN(a)?"—°":String.format(java.util.Locale.US,"%.1f°",a));
        toggle.setText(getSharedPreferences("global",0).getBoolean("enabled",false)?"暂停全局效果":"开启全局效果");
        if(foreground)main.postDelayed(this,350);
    }};
    @Override public void onCreate(Bundle state){super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(245,245,247));getWindow().setNavigationBarColor(Color.rgb(245,245,247));
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(Color.rgb(245,245,247));
        scroll.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets i=insets.getInsets(WindowInsets.Type.systemBars());v.setPadding(i.left,i.top,i.right,i.bottom);return insets;});
        LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setPadding(dp(26),dp(32),dp(26),dp(32));
        scroll.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{int side=Math.max(dp(26),((r-l)-dp(640))/2);if(page.getPaddingLeft()!=side)page.setPadding(side,dp(32),side,dp(32));});scroll.addView(page);setContentView(scroll);
        TextView eyebrow=text("FOLDLIGHT LAB · 0.2",12,Color.rgb(0,122,255));eyebrow.setLetterSpacing(.12f);page.addView(eyebrow);
        TextView title=text("折光\n全局实验",38,Color.rgb(28,28,30));title.setTypeface(null,Typeface.BOLD);page.addView(title);
        TextView intro=text("离开 App，效果依然跟随开合",17,Color.rgb(100,100,108));intro.setPadding(0,dp(14),0,dp(24));page.addView(intro);
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(22),dp(20),dp(22),dp(20));card.setBackground(background(Color.WHITE,24));page.addView(card);
        card.addView(text("实时铰链角度",13,Color.GRAY));angle=text("—°",44,Color.BLACK);card.addView(angle);
        status=text("等待开启无障碍服务",14,Color.DKGRAY);card.addView(status);
        button(page,"1  授权连续角度 · Shizuku",false,()->requestHinge());
        button(page,"2  授权屏幕效果 · 无障碍",false,()->explainAccess());
        toggle=button(page,"开启全局效果",true,()->{
            boolean next=!getSharedPreferences("global",0).getBoolean("enabled",false);
            getSharedPreferences("global",0).edit().putBoolean("enabled",next).apply();
            GlobalEffectService service=GlobalEffectService.instance;
            if(service!=null)service.setEnabled(next);else if(next)explainAccess();
            refresh.run();
        });
        button(page,"返回上个页面",false,this::finish);
        EffectSettingsView controls=new EffectSettingsView(this);
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.topMargin=dp(30);page.addView(controls,cp);
        TextView note=text("与原版独立安装\n原版折光 Fold、图片和参数全部保留。\n\n实验方式\n翻折时截取当前页面，动画期间内容短暂定格。停止开合后自动撤掉覆盖层。截图仅在内存中处理，不保存、不上传。\n\n当前范围\n先测试 Fold8 常规页面和正常屏幕方向。受保护页面、旋转方向暂时跳过。先完全合拢一次，再打开浏览器等普通 App。双屏保持点亮，提前移动当前 App 并等待新布局完成，截图遮住切换过程。内屏右半保持清晰固定。暂不支持桌面、最近任务和受保护页面。内屏不显示悬浮按钮；可返回本 App 的设置页停止实验，或锁屏恢复正常切屏。",14,Color.rgb(110,110,118));note.setPadding(0,dp(26),0,dp(20));page.addView(note);
        TextView credit=text("Made by ZK",13,Color.GRAY);credit.setGravity(Gravity.CENTER);page.addView(credit);credit.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("开源致谢").setMessage("双屏交接参考 bunkaich/Folduo。部分截屏及窗口就绪代码按 MIT 许可改编。\nCopyright (c) 2026 bunkaich\n完整许可随 APK 附带。本文代码不代表上游对 Fold8 的适配或背书。").setPositiveButton("关闭",null).show());
    }
    private void requestHinge(){
        if(!Shizuku.pingBinder()){Toast.makeText(this,"请先启动 Shizuku，再返回授权",Toast.LENGTH_LONG).show();return;}
        if(Shizuku.checkSelfPermission()==PackageManager.PERMISSION_GRANTED){Toast.makeText(this,"连续角度已授权",Toast.LENGTH_SHORT).show();return;}
        Shizuku.requestPermission(26);
    }
    private void explainAccess(){new AlertDialog.Builder(this).setTitle("允许在翻折时读取当前屏幕")
        .setMessage("全局实验通过已授权的 Shizuku 读取当前页面，无障碍服务用于显示双屏玻璃覆盖层。翻折时会把当前 App 移到另一块屏幕；双屏保持点亮，电量消耗会增加。图片只在本机内存中短暂处理，不保存、不上传。\n\n请在接下来的无障碍设置 → 已安装的应用中，开启“折光·全局实验”。原版“折光 Fold”不受影响。")
        .setNegativeButton("稍后",null).setPositiveButton("前往系统设置",(d,w)->startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))).show();}
    private TextView text(String value,int size,int color){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setLineSpacing(dp(4),1);return t;}
    private Button button(LinearLayout parent,String title,boolean primary,Runnable action){
        Button b=new Button(this);b.setText(title);b.setAllCaps(false);b.setTextSize(16);b.setTextColor(primary?Color.WHITE:Color.rgb(0,100,220));
        b.setBackground(background(primary?Color.rgb(0,122,255):Color.WHITE,18));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(58));p.topMargin=dp(13);parent.addView(b,p);b.setOnClickListener(v->action.run());return b;
    }
    private GradientDrawable background(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    @Override protected void onResume(){super.onResume();foreground=true;main.removeCallbacks(refresh);main.post(refresh);}
    @Override protected void onPause(){foreground=false;main.removeCallbacks(refresh);super.onPause();}
}
