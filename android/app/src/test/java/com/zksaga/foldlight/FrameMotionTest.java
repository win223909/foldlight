package com.zksaga.foldlight;
import org.junit.Test;
import static org.junit.Assert.*;
public class FrameMotionTest {
 @Test public void tinyOpeningIsVisibleQuicklyEvenWithHighSoftness(){
  FrameMotion motion=new FrameMotion();float x=0;
  for(int i=0;i<18;i++)x=motion.step(x,3,1/120f,.98f);
  assertTrue("early response should reach most of a small change within 150 ms",x>2.55f);
 }
 @Test public void refreshRateDoesNotChangeTheTrajectory(){
  FrameMotion a=new FrameMotion(),b=new FrameMotion();float x=0,y=0;
  for(int frame=0;frame<15;frame++){x=a.step(x,130,1/60f);y=b.step(y,130,1/120f);y=b.step(y,130,1/120f);assertEquals(x,y,.001);}
 }
 @Test public void softnessAdjustsResponseAndStillSettles(){
  FrameMotion direct=new FrameMotion(),soft=new FrameMotion();float a=0,b=0;
  for(int i=0;i<12;i++){a=direct.step(a,90,1/120f,0);b=soft.step(b,90,1/120f,1);}
  assertTrue(a>b+20);
  for(int i=0;i<240;i++)b=soft.step(b,90,1/120f,1);assertEquals(90,b,.001);
 }
 @Test public void reversalsAndEndpointsSettleWithoutSustainedDrift(){
  FrameMotion m=new FrameMotion();float x=0;
  for(int i=0;i<12;i++)x=m.step(x,120,1/120f);
  float previous=x;for(int i=0;i<120;i++){x=m.step(x,30,1/120f);assertTrue(x>=0&&x<=180);}
  assertTrue(x<previous);assertEquals(30,x,.001);
  for(int i=0;i<120;i++)x=m.step(x,180,1/120f);assertEquals(180,x,.001);
  m.reset();assertEquals(180,m.step(180,180,1/60f),0);
 }
}
