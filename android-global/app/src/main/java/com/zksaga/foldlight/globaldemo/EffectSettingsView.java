package com.zksaga.foldlight.globaldemo;

import android.content.*;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
import java.util.EnumMap;

/** Six controls, grouped identically to Foldlight Fold; preferences belong only to this app. */
final class EffectSettingsView extends LinearLayout {
    private static final int INK=0xff1d1d1f,MUTED=0xff76767c,BLUE=0xff007aff;
    private final SharedPreferences prefs;
    private final EnumMap<EffectParameters.Control,SeekBar> sliders=new EnumMap<>(EffectParameters.Control.class);
    private final EnumMap<EffectParameters.Control,TextView> values=new EnumMap<>(EffectParameters.Control.class);
    EffectSettingsView(Context context){
        super(context);prefs=context.getSharedPreferences(EffectParameters.STORE,Context.MODE_PRIVATE);setOrientation(VERTICAL);
        EffectParameters settings=EffectParameters.read(prefs.getAll());
        TextView title=label("效果设置",24,INK);title.setTypeface(null,Typeface.BOLD);addView(title);space(this,8);
        addView(label("自动保存，返回其他 App 后开合即可体验",13,MUTED));space(this,18);
        LinearLayout cover=card("外屏","打开时渐暗，折叠时渐亮");
        control(cover,EffectParameters.Control.COVER_DARK,settings);control(cover,EffectParameters.Control.COVER_LIGHT,settings);
        LinearLayout inner=card("内屏 · 左半边","打开时渐亮，折叠时渐暗。右半边保持清晰、静止");
        control(inner,EffectParameters.Control.INNER_LIGHT,settings);control(inner,EffectParameters.Control.INNER_DARK,settings);
        LinearLayout texture=card("画面质感","压缩只影响外屏右侧；模糊影响外屏和内屏左半边");
        control(texture,EffectParameters.Control.EDGE,settings);control(texture,EffectParameters.Control.BLUR,settings);
        Button reset=new Button(context);reset.setText("恢复默认参数");reset.setAllCaps(false);reset.setTextColor(BLUE);reset.setTextSize(15);reset.setBackground(round(0xffffffff));
        addView(reset,new LayoutParams(-1,dp(52)));reset.setOnClickListener(v->{
            SharedPreferences.Editor edit=prefs.edit();
            for(EffectParameters.Control c:EffectParameters.Control.values())edit.putFloat(c.key,c.stored(c.recommended));
            edit.apply();refreshControls();Toast.makeText(context,"已恢复默认参数",Toast.LENGTH_SHORT).show();
        });space(this,10);
        addView(label("默认：外屏 80° / 90°，内屏左侧 55° / 70°\n右侧压缩 60%，模糊 55%",12,MUTED));
    }
    private void refreshControls(){EffectParameters s=EffectParameters.read(prefs.getAll());for(EffectParameters.Control c:EffectParameters.Control.values()){int n=s.progress(c);sliders.get(c).setProgress(n-c.min);values.get(c).setText(c.format(n));}}
    private LinearLayout card(String title,String detail){
        LinearLayout c=new LinearLayout(getContext());c.setOrientation(VERTICAL);c.setPadding(dp(20),dp(20),dp(20),dp(8));c.setBackground(round(0xffffffff));
        LayoutParams p=new LayoutParams(-1,-2);p.bottomMargin=dp(16);addView(c,p);
        TextView heading=label(title,18,INK);heading.setTypeface(null,Typeface.BOLD);c.addView(heading);space(c,8);c.addView(label(detail,12,MUTED));space(c,18);return c;
    }
    private void control(LinearLayout parent,EffectParameters.Control c,EffectParameters settings){
        LinearLayout row=new LinearLayout(getContext());row.setGravity(Gravity.CENTER_VERTICAL);
        TextView name=label(c.title,14,INK),value=label(c.format(settings.progress(c)),14,MUTED);value.setFontFeatureSettings("tnum");
        row.addView(name,new LayoutParams(0,-2,1));row.addView(value);parent.addView(row);
        SeekBar slider=new SeekBar(getContext());slider.setMax(c.max-c.min);slider.setProgress(settings.progress(c)-c.min);
        slider.setContentDescription((c==EffectParameters.Control.COVER_DARK||c==EffectParameters.Control.COVER_LIGHT?"外屏 ":c==EffectParameters.Control.INNER_LIGHT||c==EffectParameters.Control.INNER_DARK?"内屏左半边 ":"")+c.title);
        slider.setProgressTintList(ColorStateList.valueOf(BLUE));slider.setThumbTintList(ColorStateList.valueOf(BLUE));
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){}
            public void onProgressChanged(SeekBar b,int progress,boolean user){int actual=progress+c.min;value.setText(c.format(actual));if(user)prefs.edit().putFloat(c.key,c.stored(actual)).apply();}
        });parent.addView(slider,new LayoutParams(-1,dp(48)));space(parent,8);sliders.put(c,slider);values.put(c,value);
    }
    private TextView label(String text,int size,int color){TextView t=new TextView(getContext());t.setText(text);t.setTextSize(size);t.setTextColor(color);t.setLineSpacing(dp(3),1);return t;}
    private GradientDrawable round(int color){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(22));return d;}
    private void space(LinearLayout p,int amount){p.addView(new View(getContext()),new LayoutParams(1,dp(amount)));}
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
}
