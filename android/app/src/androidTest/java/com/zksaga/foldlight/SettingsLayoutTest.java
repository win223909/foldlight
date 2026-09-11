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
        return new SettingsPanel(context,image,image,85,90,90,180,1,.2f,new SettingsPanel.Actions(){
            public void innerLightStart(float value){} public void innerDarkStart(float value){} public void darkStart(float value){} public void lightStart(float value){}
            public void localInput(){} public void photo(boolean cover){} public void fullscreen(){}
            public void defaults(){} public void edge(float value){} public void blur(float value){} public void diagnostics(){} public void reset(){}

        });
    }
    @Test public void exposesOnlyTheSixRequestedControls(){
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            java.util.ArrayList<String> names=new java.util.ArrayList<>();collectSliders(panel(),names);
            assertEquals(java.util.Arrays.asList("打开开始变暗角度","折叠开始变亮角度","打开开始变亮角度","折叠开始变暗角度","外屏右侧压缩程度","模糊程度"),names);
        });
    }
    @Test public void restoreButtonUpdatesEveryDisplayedParameter(){
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            SettingsPanel panel=panel();
            java.util.ArrayList<View> views=new java.util.ArrayList<>();flatten(panel,views);
            View reset=views.stream().filter(v->v instanceof android.widget.Button && ((android.widget.Button)v).getText().toString().equals("恢复默认参数")).findFirst().orElseThrow();
            reset.performClick();
            int[] expected={40,49,55,69,60,100};int index=0;
            for(View view:views)if(view instanceof android.widget.SeekBar)assertEquals(expected[index++],((android.widget.SeekBar)view).getProgress());
            assertEquals(6,index);
        });
    }
    private void flatten(View view,java.util.List<View> result){
        result.add(view);if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)flatten(group.getChildAt(i),result);}
    }
    private void collectSliders(View view,java.util.List<String> result){
        if(view instanceof android.widget.SeekBar)result.add(view.getContentDescription().toString());
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)collectSliders(group.getChildAt(i),result);}
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
