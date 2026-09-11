package com.zksaga.foldlight;

import org.junit.Test;
import static org.junit.Assert.*;

public class InnerDimmingTest {
    @Test public void defaultThresholdsPreservePreviousInnerTransition(){
        InnerDimming dim=new InnerDimming();
        for(int a=0;a<=180;a++)assertEquals(FoldMath.clamp((a-90)/90f,0,1),dim.step(a,90,180),.00001f);
        for(int a=180;a>=0;a--)assertEquals(FoldMath.clamp((a-90)/90f,0,1),dim.step(a,90,180),.00001f);
    }
    @Test public void openingAndClosingHaveIndependentStarts(){
        InnerDimming dim=new InnerDimming();
        assertEquals(0,dim.step(60,60,140),0);
        assertEquals(.5f,dim.step(120,60,140),.00001f);
        assertEquals(1,dim.step(180,60,140),0);
        assertEquals(1,dim.step(140,60,140),0);
        assertEquals(.5f,dim.step(95,60,140),.00001f);
        assertEquals(0,dim.step(50,60,140),0);
    }
    @Test public void partialReversalsKeepBrightnessUntilEnvelopeCatchesUp(){
        InnerDimming dim=new InnerDimming();dim.step(0,60,140);
        assertEquals(.5f,dim.step(120,60,140),.00001f);
        for(float a:new float[]{119,119.2f,110,100,95})assertEquals(.5f,dim.step(a,60,140),.00001f);
        float level=dim.step(80,60,140);
        for(float a:new float[]{80.1f,79.99999f,85,90})assertEquals(level,dim.step(a,60,140),.00001f);
    }
    @Test public void allFourThresholdsRemainIndependentAndMonotonic(){
        for(int open=0;open<=179;open++)for(int close=1;close<=180;close++){
            InnerDimming dim=new InnerDimming();float previous=0;
            for(int a=0;a<=180;a+=3){float value=dim.step(a,open,close);assertTrue(value>=previous&&value<=1);previous=value;}
            assertEquals(1,previous,0);
            for(int a=180;a>=0;a-=3){float value=dim.step(a,open,close);assertTrue(value<=previous&&value>=0);previous=value;}
            assertEquals(0,previous,0);
        }
        CoverDimming cover=new CoverDimming();InnerDimming first=new InnerDimming(),second=new InnerDimming();
        cover.step(0);first.step(0,60,140);second.step(0,120,160);
        assertNotEquals(first.step(135,60,140),second.step(135,120,160),.001f);
        float coverLevel=cover.step(135);first.step(120,10,30);assertEquals(coverLevel,cover.value(),0);
    }
    @Test public void savedStateAndInvalidAnglesDoNotResetBrightness(){
        InnerDimming restored=new InnerDimming();restored.restore(.5f);
        assertEquals(.5f,restored.step(105,60,140),0);
        for(float bad:new float[]{Float.NaN,Float.POSITIVE_INFINITY,-1,181})assertEquals(.5f,restored.step(bad,60,140),0);
        assertEquals(1,restored.step(180,60,140),0);
    }
}
