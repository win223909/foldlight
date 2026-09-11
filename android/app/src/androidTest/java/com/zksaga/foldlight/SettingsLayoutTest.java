package com.zksaga.foldlight;

import android.graphics.Bitmap;
import android.view.*;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class SettingsLayoutTest {
    private SettingsPanel panel(){
        android.content.Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        Bitmap image=Bitmap.createBitmap(4,4,Bitmap.Config.ARGB_8888);
        return new SettingsPanel(context,image,image,120,1.5f,90,.7f,.08f,true,90,90,90,180,MotionProfile.defaults(),.08f,1,1,new SettingsPanel.Actions(){
            public void innerLightStart(float value){} public void innerDarkStart(float value){} public void darkStart(float value){} public void lightStart(float value){} public void coverDimming(boolean value){} public void localInput(){} public void photo(boolean cover){} public void fullscreen(){} public void mode(String value){}
            public void manual(float value){} public void strength(float value){} public void coverMaxAngle(float value){}
            public void softness(float value){} public void blur(float value){} public void diagnostics(){} public void reset(){}
        });
    }
    @Test public void firstVisibleLayoutFitsAfterEveryPanelSwitch(){
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            SettingsPanel panel=panel();
            for(int width:new int[]{1248,2448,1248,2448}){
                panel.setVisibility(View.GONE);panel.safeInsets(104,64);panel.setVisibility(View.VISIBLE);
                panel.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1848,View.MeasureSpec.EXACTLY));
                panel.layout(0,0,width,1848);
                ViewGroup content=(ViewGroup)panel.getChildAt(0);
                for(int i=0;i<content.getChildCount();i++){
                    View child=content.getChildAt(i);if(child.getVisibility()==View.GONE)continue;
                    assertTrue("child exceeds padded right edge at width "+width+": "+child.getRight()+" > "+(width-content.getPaddingRight()),child.getRight()<=width-content.getPaddingRight());
                    assertEquals("first layout uses current left padding",content.getPaddingLeft(),child.getLeft());
                }
            }
        });
    }
}
