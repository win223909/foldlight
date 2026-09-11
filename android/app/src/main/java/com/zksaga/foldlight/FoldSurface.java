package com.zksaga.foldlight;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;

/** Both displays share the same deliberate settings gesture. Single-finger touches do nothing. */
public final class FoldSurface extends GLSurfaceView {
    static final long HOLD_MS=650,JOIN_MS=350;
    private final float slop;
    private Runnable settings;
    private int first=-1,second=-1;
    private float firstX,firstY,secondX,secondY;
    private long firstDown;
    private boolean eligible,holding;
    private final Runnable openSettings=()->{
        if(!holding||!isShown()||getWindowVisibility()!=VISIBLE)return;
        cancelGesture();
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        if(settings!=null)settings.run();
    };
    public FoldSurface(Context context){
        super(context);
        slop=Math.max(ViewConfiguration.get(context).getScaledTouchSlop()*2,16*getResources().getDisplayMetrics().density);
        setContentDescription("折光画面，双指长按打开设置");
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }
    void setSettingsAction(Runnable action){settings=action;}
    private void cancelGesture(){removeCallbacks(openSettings);eligible=false;holding=false;}
    @Override public boolean onTouchEvent(MotionEvent event){
        switch(event.getActionMasked()){
            case MotionEvent.ACTION_DOWN:
                cancelGesture();eligible=true;first=event.getPointerId(0);second=-1;
                firstX=event.getX(0);firstY=event.getY(0);firstDown=event.getEventTime();break;
            case MotionEvent.ACTION_POINTER_DOWN:
                if(!eligible||event.getPointerCount()!=2||event.getEventTime()-firstDown>JOIN_MS){cancelGesture();break;}
                int index=event.getActionIndex();second=event.getPointerId(index);
                secondX=event.getX(index);secondY=event.getY(index);
                if(moved(event,first,firstX,firstY)){cancelGesture();break;}
                holding=true;postDelayed(openSettings,HOLD_MS);break;
            case MotionEvent.ACTION_MOVE:
                if(moved(event,first,firstX,firstY)||(second!=-1&&moved(event,second,secondX,secondY)))cancelGesture();break;
            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                cancelGesture();break;
        }
        return true;
    }
    private boolean moved(MotionEvent event,int id,float x,float y){
        int index=event.findPointerIndex(id);
        return index<0||Math.hypot(event.getX(index)-x,event.getY(index)-y)>slop;
    }
    @Override public void onPause(){cancelGesture();super.onPause();}
    @Override protected void onDetachedFromWindow(){cancelGesture();super.onDetachedFromWindow();}
    @Override protected void onWindowVisibilityChanged(int visibility){super.onWindowVisibilityChanged(visibility);if(visibility!=VISIBLE)cancelGesture();}
    @Override public boolean performClick(){super.performClick();return true;}
    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info){
        super.onInitializeAccessibilityNodeInfo(info);
        info.addAction(new AccessibilityNodeInfo.AccessibilityAction(AccessibilityNodeInfo.ACTION_LONG_CLICK,"打开设置"));
    }
    @Override public boolean performAccessibilityAction(int action,Bundle args){
        if(action==AccessibilityNodeInfo.ACTION_LONG_CLICK&&settings!=null){cancelGesture();settings.run();return true;}
        return super.performAccessibilityAction(action,args);
    }
}
