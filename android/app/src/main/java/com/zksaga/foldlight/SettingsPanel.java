package com.zksaga.foldlight;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;

/** Native, scrollable settings with correctly proportioned independent screen previews. */
final class SettingsPanel extends ScrollView {
    interface Actions {
        void localInput();void photo(boolean cover);void fullscreen();
        void darkStart(float value);void lightStart(float value);void innerLightStart(float value);void innerDarkStart(float value);
        void edge(float value);void blur(float value);void diagnostics();void reset();void defaults();
    }
    private static final int INK=0xff1d1d1f,MUTED=0xff76767c,BLUE=0xff007aff;
    private final LinearLayout content;
    private final Actions actions;
    private final TextView connection,localStatus;
    private final ImageView coverImage,innerImage;
    private final java.util.List<SeekBar> parameterSliders=new java.util.ArrayList<>();
    private final java.util.List<TextView> parameterLabels=new java.util.ArrayList<>();
    private final java.util.List<Integer> parameterMins=new java.util.ArrayList<>();
    private int safeTop,safeBottom;
    private boolean ready=true;
    void setReady(boolean value){ready=value;setImportantForAccessibility(value?IMPORTANT_FOR_ACCESSIBILITY_AUTO:IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);}
    @Override public boolean onInterceptTouchEvent(MotionEvent event){return !ready||super.onInterceptTouchEvent(event);}
    @Override public boolean onTouchEvent(MotionEvent event){return !ready||super.onTouchEvent(event);}
    SettingsPanel(Context context,Bitmap cover,Bitmap inner,float darkStart,float lightStart,float innerLightStart,float innerDarkStart,float edge,float blur,Actions actions){
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
        heading("外屏");space(8);
        content.addView(label("打开时渐暗，折叠时渐亮。",12,MUTED));space(16);
        control("打开开始变暗角度",40,179,Math.round(darkStart),v->v+"°",v->actions.darkStart(v));
        control("折叠开始变亮角度",41,180,Math.round(lightStart),v->v+"°",v->actions.lightStart(v));
        space(16);divider();space(24);
        heading("内屏 · 左半边");space(8);
        content.addView(label("打开时渐亮，折叠时渐暗。右半边保持清晰、静止。",12,MUTED));space(16);
        control("打开开始变亮角度",0,179,Math.round(innerLightStart),v->v+"°",v->actions.innerLightStart(v));
        control("折叠开始变暗角度",1,180,Math.round(innerDarkStart),v->v+"°",v->actions.innerDarkStart(v));
        space(16);divider();space(24);
        heading("画面质感");space(8);
        content.addView(label("压缩仅影响外屏右侧，左侧固定。\n模糊同时影响外屏和内屏左半边。",12,MUTED));space(16);
        control("外屏右侧压缩程度",0,100,Math.round(edge*100),v->v+"%",v->actions.edge(v/100f));
        control("模糊程度",0,100,Math.round(blur*100),v->v+"%",v->actions.blur(v/100f));
        space(16);divider();space(10);
        content.addView(button("恢复默认参数",false,()->{actions.defaults();restoreDefaultControls();}),new LinearLayout.LayoutParams(-1,dp(48)));
        content.addView(label("推荐：外屏 80° / 90°，内屏左侧 55° / 70°\n右侧压缩 60%，模糊 100%",12,MUTED));space(12);
        content.addView(button("恢复默认图片",false,actions::reset),new LinearLayout.LayoutParams(-1,dp(48)));
        content.addView(button("连接与诊断",false,actions::diagnostics),new LinearLayout.LayoutParams(-1,dp(48)));space(16);
        TextView credit=label("Made by ZK",13,MUTED);credit.setGravity(Gravity.CENTER);credit.setLetterSpacing(.025f);content.addView(credit);space(12);
    }
    private void restoreDefaultControls(){
        int[] values={Math.round(EffectDefaults.DARK_START),Math.round(EffectDefaults.LIGHT_START),Math.round(EffectDefaults.INNER_LIGHT_START),Math.round(EffectDefaults.INNER_DARK_START),Math.round(EffectDefaults.EDGE*100),Math.round(EffectDefaults.SIMPLE_BLUR*100)};
        for(int i=0;i<values.length;i++){parameterSliders.get(i).setProgress(values[i]-parameterMins.get(i));parameterLabels.get(i).setText(values[i]+(i<4?"°":"%"));}
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

    }
    private interface IntChange{void apply(int value);}
    private interface ValueLabel{String text(int value);}
    private SeekBar slider(int max,int progress,String title,IntChange change){SeekBar s=new SeekBar(getContext());s.setMax(max);s.setProgress(progress);s.setProgressTintList(ColorStateList.valueOf(BLUE));s.setThumbTintList(ColorStateList.valueOf(BLUE));s.setContentDescription(title);s.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar bar){}public void onStopTrackingTouch(SeekBar bar){}public void onProgressChanged(SeekBar bar,int value,boolean user){if(user)change.apply(value);}});return s;}
    private void control(String title,int min,int max,int initial,ValueLabel format,IntChange change){
        LinearLayout row=row();TextView name=label(title,14,INK),value=label(format.text(initial),14,MUTED);row.addView(name,new LinearLayout.LayoutParams(0,-2,1));row.addView(value);content.addView(row);
        SeekBar s=slider(max-min,initial-min,title,p->{value.setText(format.text(p+min));change.apply(p+min);});parameterSliders.add(s);parameterLabels.add(value);parameterMins.add(min);content.addView(s,new LinearLayout.LayoutParams(-1,dp(48)));space(8);
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
