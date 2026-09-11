package com.zksaga.foldlight;
import org.junit.Test;
import static org.junit.Assert.*;

public class DeformationMotionTest {
    private float settle(DeformationMotion m,float angle,MotionProfile[] p){float value=0;for(int i=0;i<180;i++)value=m.step(angle,1/120f,100,.9f,p[0],p[1]);return value;}
    @Test public void thresholdsHoldBothScreensUntilEachDirectionStarts(){
        for(boolean cover:new boolean[]{true,false}){
            MotionProfile[] p=MotionProfile.defaults();p[0].start=40;p[1].start=120;
            DeformationMotion m=new DeformationMotion(cover);float initial=settle(m,0,p);
            assertEquals(initial,settle(m,30,p),.001f);
            assertTrue(Math.abs(initial-settle(m,80,p))>5);
            float full=settle(m,180,p);assertEquals(full,settle(m,140,p),.001f);
            assertTrue(Math.abs(full-settle(m,70,p))>5);
        }
    }
    @Test public void speedChangesCatchupWithoutOvershoot(){
        MotionProfile[] slow=MotionProfile.defaults(),fast=MotionProfile.defaults();slow[0].speed=.25f;fast[0].speed=4;
        DeformationMotion a=new DeformationMotion(true),b=new DeformationMotion(true);settle(a,0,slow);settle(b,0,fast);
        float low=a.step(90,1/120f,100,.9f,slow[0],slow[1]),high=b.step(90,1/120f,100,.9f,fast[0],fast[1]);
        assertTrue(high>low);assertTrue(high<100);assertEquals(settle(a,90,slow),settle(b,90,fast),.01f);
    }
    @Test public void accelerationShapesEarlyTravel(){
        MotionProfile[] early=MotionProfile.defaults(),late=MotionProfile.defaults();early[0].acceleration=.5f;late[0].acceleration=2;
        DeformationMotion a=new DeformationMotion(true),b=new DeformationMotion(true);settle(a,0,early);settle(b,0,late);
        assertTrue(settle(a,45,early)>settle(b,45,late)+10);
        assertEquals(100,settle(a,180,early),.001f);assertEquals(100,settle(b,180,late),.001f);
    }
    @Test public void amplitudesAffectAllFourLegsIndependently(){
        for(boolean cover:new boolean[]{true,false})for(boolean opening:new boolean[]{true,false}){
            MotionProfile[] weak=MotionProfile.defaults(),strong=MotionProfile.defaults();int i=opening?0:1;weak[i].amplitude=.5f;strong[i].amplitude=1.5f;
            DeformationMotion a=new DeformationMotion(cover),b=new DeformationMotion(cover);float start=opening?0:180;
            settle(a,start,weak);settle(b,start,strong);
            assertTrue("amplitude must change every leg",Math.abs(settle(a,90,weak)-settle(b,90,strong))>2);
        }
    }
    @Test public void reversalAndRecreationHaveNoPositionJump(){
        MotionProfile[] p=MotionProfile.defaults();p[0].amplitude=1.7f;p[1].amplitude=.4f;
        DeformationMotion m=new DeformationMotion(true);settle(m,0,p);float previous=settle(m,110,p);
        assertEquals(previous,m.step(109,0,100,.9f,p[0],p[1]),0);
        DeformationMotion restored=new DeformationMotion(true);restored.restore(m.state());
        for(int a=109;a>=0;a--){float x=m.step(a,.01f,100,.9f,p[0],p[1]);assertEquals(x,restored.step(a,.01f,100,.9f,p[0],p[1]),0);assertTrue(x<=previous+.001f);previous=x;}
        assertEquals(0,settle(m,0,p),.001f);
    }
    @Test public void extremeProfilesStayFiniteAndBoundedAcrossRepeatedCycles(){
        for(boolean cover:new boolean[]{true,false}){
            MotionProfile[] p={new MotionProfile(30,.25f,2,.25f),new MotionProfile(120,4,2,3)};
            DeformationMotion m=new DeformationMotion(cover);
            for(int cycle=0;cycle<3;cycle++)for(int i=0;i<=360;i++){
                float v=m.step(i<=180?i:360-i,.016f,180,2.5f,p[0],p[1]);assertTrue(Float.isFinite(v));assertTrue(v>=0&&v<=(cover?180:85));
            }
        }
    }
    @Test public void zeroAmplitudeDisablesOnlyThatLegAndInvalidSamplesKeepPose(){
        MotionProfile[] p=MotionProfile.defaults();p[1].amplitude=0;
        DeformationMotion m=new DeformationMotion(true);settle(m,0,p);float full=settle(m,180,p);
        assertEquals(full,settle(m,0,p),0);assertEquals(full,m.step(Float.NaN,.1f,100,.9f,p[0],p[1]),0);
    }
}
