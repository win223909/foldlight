package com.zksaga.foldlight;

import org.junit.Test;
import static org.junit.Assert.*;

public class CoverDimmingTest {
    @Test public void completeCycleUsesDifferentOpeningAndClosingThresholds(){
        CoverDimming dim=new CoverDimming();
        for(int a=0;a<=85;a++)assertEquals(1,dim.step(a),0);
        float previous=1;
        for(int a=86;a<=180;a++){float level=dim.step(a);assertTrue(level<=previous);previous=level;}
        assertEquals(0,previous,0);
        for(int a=180;a>=90;a--)assertEquals(0,dim.step(a),0);
        assertEquals(.5f,dim.step(65),.0001f);
        assertEquals(1,dim.step(40),0);
        assertEquals(1,dim.step(0),0);
    }
    @Test public void partialReversalsAndSmallSensorJitterDoNotFlash(){
        CoverDimming dim=new CoverDimming();dim.step(0);
        assertEquals(.5f,dim.step(132.5f),.0001f);
        for(float a:new float[]{132,132.2f,125,100,90,70,65})assertEquals(.5f,dim.step(a),.0001f);
        float partial=dim.step(50);
        for(float a:new float[]{50.1f,49.999f,70,90,100})assertEquals(partial,dim.step(a),.0001f);
        assertTrue(dim.step(150)<partial);
    }
    @Test public void customizedStartsUsePhysicalAnglesAndKeepFixedEndpoints(){
        CoverDimming dim=new CoverDimming();
        assertEquals(1,dim.step(120,120,70),0);
        assertEquals(.5f,dim.step(150,120,70),.0001f);
        assertEquals(0,dim.step(180,120,70),0);
        assertEquals(0,dim.step(70,120,70),0);
        assertEquals(.5f,dim.step(55,120,70),.0001f);
        assertEquals(1,dim.step(40,120,70),0);
    }
    @Test public void allSelectableStartPairsRemainBoundedAndMonotonic(){
        for(int dark=40;dark<=179;dark++)for(int light=41;light<=180;light++){
            CoverDimming dim=new CoverDimming();float previous=1;
            for(int a=0;a<=180;a+=3){float value=dim.step(a,dark,light);assertTrue(value>=0&&value<=previous);previous=value;}
            assertEquals(0,previous,0);
            for(int a=180;a>=0;a-=3){float value=dim.step(a,dark,light);assertTrue(value<=1&&value>=previous);previous=value;}
            assertEquals(1,previous,0);
        }
    }
    @Test public void restorationAndInvalidSamplesKeepTheCurrentLevel(){
        CoverDimming dim=new CoverDimming();assertEquals(0,dim.step(180),0);
        CoverDimming restored=new CoverDimming();restored.restore(dim.value());
        assertEquals(0,restored.step(100),0);assertEquals(.5f,restored.step(65),.0001f);
        for(float bad:new float[]{Float.NaN,Float.POSITIVE_INFINITY,-1,181})assertEquals(.5f,restored.step(bad),.0001f);
        assertEquals(1,restored.step(40),0);
    }
}
