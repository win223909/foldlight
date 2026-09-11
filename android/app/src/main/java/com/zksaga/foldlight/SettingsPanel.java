package com.zksaga.foldlight;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;
import java.util.Locale;

/** Native, scrollable settings with correctly proportioned independent screen previews. */
final class SettingsPanel extends ScrollView {
    interface Actions {
        void localInput();void photo(boolean cover);void fullscreen();void mode(String value);
        void manual(float value);void strength(float value);void coverMaxAngle(float value);void softness(float value);void blur(float value);
        default void profile(int index,MotionProfile value){} default void glass(boolean cover,float intensity,float gradient){}
        void darkStart(float value);void lightStart(float value);void innerLightStart(float value);void innerDarkStart(float value);void coverDimming(boolean value);void diagnostics();void reset();
    }
    private static final int INK=0xff1d1d1f,MUTED=0xff76767c,BLUE=0xff007aff;
    private final LinearLayout content;
    private final Actions actions;
    private final TextView connection,angleText,localStatus;
    private final ImageView coverImage,innerImage;
    private final Button[] modes=new Button[3];
    private final LinearLayout manualRow;
    private int safeTop,safeBottom;
    private String selected="";
    private boolean ready=true;
    void setReady(boolean value){ready=value;setImportantForAccessibility(value?IMPORTANT_FOR_ACCESSIBILITY_AUTO:IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);}
    @Override public boolean onInterceptTouchEvent(MotionEvent event){return !ready||super.onInterceptTouchEvent(event);}
    @Override public boolean onTouchEvent(MotionEvent event){return !ready||super.onTouchEvent(event);}
    SettingsPanel(Context context,Bitmap cover,Bitmap inner,float manual,float strength,float coverMaxAngle,float softness,float blur,boolean coverDimming,float darkStart,float lightStart,float innerLightStart,float innerDarkStart,MotionProfile[] profiles,float innerFrost,float coverGradient,float innerGradient,Actions actions){
        super(context);this.actions=actions;setFillViewport(true);setClipToPadding(false);setVerticalScrollBarEnabled(false);setBackgroundColor(0xfff5f5f7);
        content=new LinearLayout(context);content.setOrientation(LinearLayout.VERTICAL);addView(content,new ScrollView.LayoutParams(-1,-2));
        LinearLayout mast=row();TextView wordmark=label("FOLDLIGHT",11,MUTED);wordmark.setLetterSpacing(.16f);mast.addView(wordmark,new LinearLayout.LayoutParams(0,-2,1));
        TextView version=label(BuildConfig.VERSION_NAME,12,MUTED);mast.addView(version);content.addView(mast);space(10);
        TextView title=label("折光",36,INK);title.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));content.addView(title);space(7);
        connection=label("连接手机感应，开始体验",13,MUTED);content.addView(connection);space(26);
        LinearLayout previews=row();previews.setGravity(Gravity.BOTTOM);
        coverImage=preview(previews,true,cover);innerImage=preview(previews,false,inner);content.addView(previews);space(22);
        Button start=button("全屏体验",true,actions::fullscreen);content.addView(start,new LinearLayout.LayoutParams(-1,dp(54)));space(9);
        TextView hint=label("双指同时长按，打开设置",11,MUTED);hint.setGravity(Gravity.CENTER);content.addView(hint);space(14);
        content.addView(button("启用免 USB 感应",false,actions::localInput),new LinearLayout.LayoutParams(-1,dp(48)));
        localStatus=label("Shizuku 授权后，无需连接电脑",12,MUTED);localStatus.setGravity(Gravity.CENTER);content.addView(localStatus);space(24);divider();space(24);
        heading("效果调节");space(13);
        LinearLayout segments=row();segments.setPadding(dp(4),dp(4),dp(4),dp(4));segments.setBackground(round(0xffe6e6eb,14));
        String[] names={"跟随开合","动画演示","手动"},values={"sensor","auto","manual"};
        for(int i=0;i<3;i++){final String mode=values[i];modes[i]=button(names[i],false,()->actions.mode(mode));segments.addView(modes[i],new LinearLayout.LayoutParams(0,dp(44),1));}content.addView(segments);space(12);
        manualRow=new LinearLayout(context);manualRow.setOrientation(LinearLayout.VERTICAL);
        angleText=label("预览角度 · "+Math.round(manual)+"°",13,INK);manualRow.addView(angleText);
        SeekBar angle=slider(180,(int)manual,"手动预览角度",v->{angleText.setText("预览角度 · "+v+"°");actions.manual(v);});manualRow.addView(angle,new LinearLayout.LayoutParams(-1,dp(44)));content.addView(manualRow);
        LinearLayout jumps=row();
        Button outerJump=button("外屏",false,()->{}),innerJump=button("内屏左半边",false,()->{}),sharedJump=button("共用",false,()->{});
        for(Button b:new Button[]{outerJump,innerJump,sharedJump})jumps.addView(b,new LinearLayout.LayoutParams(0,dp(44),1));content.addView(jumps);
        space(12);TextView outerHeading=heading("外屏");space(8);
        content.addView(label("画面绕左侧边缘转动；以下参数仅影响外屏。",12,MUTED));space(16);
        control("外屏基准转角",0,180,Math.round(coverMaxAngle),v->v+"°",v->actions.coverMaxAngle(v));
        Switch dimming=new Switch(context);dimming.setText("外屏渐暗");dimming.setTextSize(14);dimming.setTextColor(INK);dimming.setChecked(coverDimming);
        dimming.setThumbTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{BLUE,0xffb8b8be}));
        dimming.setOnCheckedChangeListener((button,checked)->actions.coverDimming(checked));content.addView(dimming,new LinearLayout.LayoutParams(-1,dp(48)));space(12);
        motionGroup("外屏 · 展开",0,profiles);
        control("外屏 · 展开开始变暗",40,179,Math.round(darkStart),v->v+"°",v->actions.darkStart(v));
        motionGroup("外屏 · 折叠",1,profiles);
        control("外屏 · 合拢开始变亮",41,180,Math.round(lightStart),v->v+"°",v->actions.lightStart(v));
        content.addView(label("外屏 180° 全黑、40° 恢复全亮。黑色画面不代表息屏。",12,MUTED));space(16);glassControls(true,blur,coverGradient);space(24);divider();space(24);
        TextView innerHeading=heading("内屏 · 左半边");space(8);
        content.addView(label("右半边保持清晰、静止。以下参数只影响左半边。",12,MUTED));space(16);
        control("内屏左侧基准幅度",25,250,Math.round(strength*100),v->String.format(Locale.US,"%.2g×",v/100f),v->actions.strength(v/100f));
        final float[] innerStarts={innerLightStart,innerDarkStart};
        TextView innerHint=label("",12,MUTED);
        Runnable updateInnerHint=()->innerHint.setText("展开到 180° 完全清晰；合拢到 "+Math.round(InnerDimming.closingEnd(innerStarts[0],innerStarts[1]))+"° 完全变暗。");
        motionGroup("内屏左侧 · 展开",2,profiles);
        control("内屏左侧 · 展开开始变亮",0,179,Math.round(innerLightStart),v->v+"°",v->{innerStarts[0]=v;actions.innerLightStart(v);updateInnerHint.run();});
        motionGroup("内屏左侧 · 折叠",3,profiles);
        control("内屏左侧 · 合拢开始变暗",1,180,Math.round(innerDarkStart),v->v+"°",v->{innerStarts[1]=v;actions.innerDarkStart(v);updateInnerHint.run();});
        updateInnerHint.run();content.addView(innerHint);space(16);glassControls(false,innerFrost,innerGradient);space(24);divider();space(24);
        TextView sharedHeading=heading("共用 · 跟随与调节说明");space(12);
        outerJump.setOnClickListener(v->smoothScrollTo(0,outerHeading.getTop()));innerJump.setOnClickListener(v->smoothScrollTo(0,innerHeading.getTop()));sharedJump.setOnClickListener(v->smoothScrollTo(0,sharedHeading.getTop()));
        content.addView(label("速度越高，跟随越快。加速度 1× 为原曲线，\n大于 1× 前慢后快，小于 1× 前快后慢。\n幅度以本屏基准为参考，1× 为原幅度，0× 保持该次动作起始形态。\n外屏最多转动 180°，内屏左侧最多 85°。\n调节变形参数后，从当前位置继续开合即可预览。",12,MUTED));space(16);
        control("跟随柔和度",0,100,Math.round(softness*100),v->v+"%",v->actions.softness(v/100f));
        TextView softnessHint=label("低：响应直接　高：过渡柔和、稍有延迟",12,MUTED);content.addView(softnessHint);space(16);
        TextView motionHint=label("高斯玻璃模糊与柔边沿转轴向外渐强。\n内屏左侧按设定角度逐渐变亮或变暗，\n右侧始终保持清晰、静止。",12,MUTED);content.addView(motionHint);space(23);divider();space(10);
        content.addView(button("恢复默认图片",false,actions::reset),new LinearLayout.LayoutParams(-1,dp(48)));
        content.addView(button("连接与诊断",false,actions::diagnostics),new LinearLayout.LayoutParams(-1,dp(48)));space(16);
        TextView credit=label("Made by ZK",13,MUTED);credit.setGravity(Gravity.CENTER);credit.setLetterSpacing(.025f);content.addView(credit);space(12);
    }
    private void glassControls(boolean cover,float intensity,float gradient){
        String name=cover?"外屏":"内屏左侧";float[] values={intensity,gradient};
        control(name+" · 磨砂强度",0,100,Math.round(intensity*100),v->v+"%",v->{values[0]=v/100f;actions.glass(cover,values[0],values[1]);});
        control(name+" · 磨砂渐变强度",0,100,Math.round(gradient*100),v->v+"%",v->{values[1]=v/100f;actions.glass(cover,values[0],values[1]);});
        content.addView(label("渐变 0%：均匀磨砂；100%：从转轴向外渐强。\n同时影响边缘柔化。",12,MUTED));
    }
    private void motionGroup(String title,int index,MotionProfile[] profiles){
        space(12);TextView heading=label(title,16,INK);heading.setTypeface(null,Typeface.BOLD);content.addView(heading);space(12);
        MotionProfile initial=profiles[index];final float[] values={initial.start,initial.speed,initial.amplitude,initial.acceleration};
        Runnable changed=()->actions.profile(index,new MotionProfile(values[0],values[1],values[2],values[3]));
        control(title+"开始变形",0,180,Math.round(values[0]),v->v+"°",v->{values[0]=v;changed.run();});
        control(title+" · 速度",25,400,Math.round(values[1]*100),v->String.format(Locale.US,"%.2f×",v/100f),v->{values[1]=v/100f;changed.run();});
        control(title+" · 幅度",0,200,Math.round(values[2]*100),v->String.format(Locale.US,"%.2f×",v/100f),v->{values[2]=v/100f;changed.run();});
        control(title+" · 加速度",25,300,Math.round(values[3]*100),v->String.format(Locale.US,"%.2f×",v/100f),v->{values[3]=v/100f;changed.run();});
    }
    void localStatus(String message){localStatus.setText(message);}
    void safeInsets(int top,int bottom){if(safeTop!=top||safeBottom!=bottom){safeTop=top;safeBottom=bottom;requestLayout();}}
    @Override protected void onMeasure(int widthSpec,int heightSpec){
        // Child widths must use the new panel's padding in this very measurement pass.
        updatePadding(MeasureSpec.getSize(widthSpec));super.onMeasure(widthSpec,heightSpec);
    }
    private void updatePadding(int width){int side=Math.max(dp(24),(width-dp(600))/2);content.setPadding(side,safeTop+dp(22),side,safeBottom+dp(24));}
    private ImageView preview(LinearLayout parent,boolean cover,Bitmap image){
        LinearLayout column=new LinearLayout(getContext());column.setOrientation(LinearLayout.VERTICAL);column.setGravity(Gravity.CENTER_HORIZONTAL);
        parent.addView(column,new LinearLayout.LayoutParams(0,-2,cover?1:1.65f));
        FrameLayout area=new FrameLayout(getContext());column.addView(area,new LinearLayout.LayoutParams(-1,dp(200)));
        FrameLayout device=new FrameLayout(getContext());device.setPadding(dp(4),dp(4),dp(4),dp(4));device.setBackground(round(0xff292a2e,cover?20:15));
        int width=dp(cover?100:182),height=Math.round(width*(cover?1972f/1248:1848f/2448));
        FrameLayout.LayoutParams bounds=new FrameLayout.LayoutParams(width+dp(8),height+dp(8),Gravity.CENTER);area.addView(device,bounds);
        ImageView picture=new ImageView(getContext());picture.setScaleType(ImageView.ScaleType.CENTER_CROP);picture.setImageBitmap(image);picture.setBackground(round(0xffe6e6eb,cover?16:11));picture.setClipToOutline(true);device.addView(picture,new FrameLayout.LayoutParams(-1,-1));
        if(!cover){View seam=new View(getContext());seam.setBackgroundColor(0x403c2c28);device.addView(seam,new FrameLayout.LayoutParams(dp(1),-1,Gravity.CENTER));}
        device.setContentDescription(cover?"更换外屏图片，竖屏":"更换内屏图片，横屏");device.setFocusable(true);device.setOnClickListener(v->actions.photo(cover));
        device.setOnTouchListener((v,event)->{if(ValueAnimator.areAnimatorsEnabled()){if(event.getAction()==MotionEvent.ACTION_DOWN)v.animate().scaleX(.97f).scaleY(.97f).setDuration(100);else if(event.getAction()==MotionEvent.ACTION_UP||event.getAction()==MotionEvent.ACTION_CANCEL)v.animate().scaleX(1).scaleY(1).setDuration(140);}return false;});
        TextView name=label(cover?"外屏图片":"内屏图片",15,INK);name.setTypeface(null,Typeface.BOLD);column.addView(name);space(column,5);
        column.addView(label(cover?"1248 × 1972":"2448 × 1848",11,MUTED));space(column,5);
        TextView change=label("轻点更换",12,BLUE);column.addView(change);change.setOnClickListener(v->actions.photo(cover));
        return picture;
    }
    void picture(boolean cover,Bitmap bitmap){ImageView target=cover?coverImage:innerImage;target.animate().cancel();target.setImageBitmap(bitmap);if(ValueAnimator.areAnimatorsEnabled()){target.setAlpha(.3f);target.animate().alpha(1).setDuration(180);}else target.setAlpha(1);}
    void update(String mode,float raw,boolean bridge,boolean active,boolean busy){
        connection.setText(busy?"正在准备图片…":active?"● 感应已连接 · "+Math.round(raw)+"°":bridge?"● 感应已连接 · 等待开合":"实时感应未连接 · 请启用手机本地辅助");
        connection.setTextColor(bridge?0xff37754b:MUTED);
        if(!selected.equals(mode)){selected=mode;String[] keys={"sensor","auto","manual"};for(int i=0;i<3;i++){boolean on=keys[i].equals(mode);modes[i].setBackground(round(on?0xffffffff:0x00000000,11));modes[i].setTextColor(on?INK:MUTED);modes[i].setTypeface(null,on?Typeface.BOLD:Typeface.NORMAL);}manualRow.setVisibility(mode.equals("manual")?VISIBLE:GONE);}
    }
    private interface IntChange{void apply(int value);}
    private interface ValueLabel{String text(int value);}
    private SeekBar slider(int max,int progress,String title,IntChange change){SeekBar s=new SeekBar(getContext());s.setMax(max);s.setProgress(progress);s.setProgressTintList(ColorStateList.valueOf(BLUE));s.setThumbTintList(ColorStateList.valueOf(BLUE));s.setContentDescription(title);s.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar bar){}public void onStopTrackingTouch(SeekBar bar){}public void onProgressChanged(SeekBar bar,int value,boolean user){if(user)change.apply(value);}});return s;}
    private void control(String title,int min,int max,int initial,ValueLabel format,IntChange change){
        LinearLayout row=row();TextView name=label(title,14,INK),value=label(format.text(initial),14,MUTED);row.addView(name,new LinearLayout.LayoutParams(0,-2,1));row.addView(value);content.addView(row);
        SeekBar s=slider(max-min,initial-min,title,p->{value.setText(format.text(p+min));change.apply(p+min);});content.addView(s,new LinearLayout.LayoutParams(-1,dp(48)));space(8);
    }
    private Button button(String title,boolean primary,Runnable action){Button b=new Button(getContext());b.setText(title);b.setTextSize(14);b.setAllCaps(false);b.setMinWidth(0);b.setMinimumWidth(0);b.setMinHeight(0);b.setMinimumHeight(0);b.setPadding(dp(8),0,dp(8),0);b.setTextColor(primary?Color.WHITE:BLUE);b.setTypeface(null,primary?Typeface.BOLD:Typeface.NORMAL);b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x18007aff),round(primary?BLUE:Color.TRANSPARENT,15),round(Color.WHITE,15)));b.setOnClickListener(v->action.run());return b;}
    private GradientDrawable round(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private LinearLayout row(){LinearLayout r=new LinearLayout(getContext());r.setGravity(Gravity.CENTER_VERTICAL);return r;}
    private TextView label(String value,int size,int color){TextView t=new TextView(getContext());t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setFontFeatureSettings("tnum");return t;}
    private TextView heading(String title){TextView t=label(title,18,INK);t.setTypeface(null,Typeface.BOLD);content.addView(t);return t;}
    private void space(int size){space(content,size);}private void space(LinearLayout parent,int size){parent.addView(new View(getContext()),new LinearLayout.LayoutParams(1,dp(size)));}
    private void divider(){View v=new View(getContext());v.setBackgroundColor(0xffdedee3);content.addView(v,new LinearLayout.LayoutParams(-1,dp(1)));}
    private int dp(float value){return Math.round(value*getResources().getDisplayMetrics().density);}
}
