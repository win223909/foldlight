package com.zksaga.foldlight;
import org.junit.Test;
import static org.junit.Assert.*;
public class FoldMathTest {
 @Test public void closedFlatAndInvalidSensor() {
  assertEquals(0,FoldMath.panelTilt(180),0);assertTrue(FoldMath.panelTilt(90)>45);
  assertEquals(85,FoldMath.panelTilt(0),0);assertFalse(FoldMath.validAngle(Float.NaN));
  assertFalse(FoldMath.validAngle(-1));assertFalse(FoldMath.validAngle(360));assertTrue(FoldMath.validAngle(179.7f));
 }
 @Test public void foldingRemainsContinuousBeyondTheFormerHardLimit() {
  for(float strength:new float[]{.5f,1,1.5f,2}) {
   float previous=0;
   for(int hinge=179;hinge>=0;hinge--){float tilt=FoldMath.panelTilt(hinge,strength);assertTrue(tilt>previous);assertTrue(tilt<=85.001f);previous=tilt;}
   assertEquals(85*Math.min(strength,1),previous,.001);
  }
  assertTrue(FoldMath.panelTilt(90,1.5f)>FoldMath.panelTilt(90,1));
 }
 @Test public void coverRespondsThroughoutThePhysicalTravelInBothDirections() {
  assertEquals(180,FoldMath.coverHinge(0),0);assertEquals(0,FoldMath.coverHinge(180),0);
  float previous=0;
  for(int angle=1;angle<=180;angle++) {
   float tilt=FoldMath.panelTilt(FoldMath.coverHinge(angle),1);
   assertTrue("cover must keep moving at "+angle,tilt>previous);previous=tilt;
  }
  for(int angle=179;angle>=0;angle--) {
   float tilt=FoldMath.panelTilt(FoldMath.coverHinge(angle),1);
   assertTrue("cover must reverse at "+angle,tilt<previous);previous=tilt;
  }
 }
 @Test public void smoothingIndependentOfRefreshRate() {
  float a=0,b=0;for(int i=0;i<60;i++)a=FoldMath.smooth(a,90,1/60f);for(int i=0;i<120;i++)b=FoldMath.smooth(b,90,1/120f);
  assertEquals(90,a,.03);assertEquals(a,b,.03);
  float reversed=FoldMath.smooth(80,10,.016f);assertTrue(reversed<80 && reversed>10);
 }
 @Test public void selectedCoverMaximumIsExactWithoutEarlySaturation() {
  for(int maximum=0;maximum<=180;maximum++) {
   assertEquals(0,FoldMath.coverTilt(0,maximum),0);
   assertEquals(maximum,FoldMath.coverTilt(180,maximum),.0001);
   float previous=0;
   for(int physical=1;physical<=180;physical++) {
    float visual=FoldMath.coverTilt(physical,maximum);
    assertTrue(visual<=maximum+.0001f);
    if(maximum>0)assertTrue(visual>previous);else assertEquals(0,visual,0);
    previous=visual;
   }
  }
  assertTrue(FoldMath.coverTilt(5,100)>7 && FoldMath.coverTilt(5,100)<8);
  assertTrue(FoldMath.coverTilt(10,100)>12 && FoldMath.coverTilt(10,100)<14);
  // There is no threshold switch at 5/10 degrees or the end of the early response.
  for(float a:new float[]{5,10,15,25,40}){
   float before=(FoldMath.coverTilt(a,100)-FoldMath.coverTilt(a-.01f,100))/.01f;
   float after=(FoldMath.coverTilt(a+.01f,100)-FoldMath.coverTilt(a,100))/.01f;
   assertEquals(before,after,.015);
  }
  assertEquals(34,FoldMath.defaultCoverMaxAngle(.4f),.0001);
  assertEquals(37,FoldMath.defaultCoverMaxAngle(.44f),0);
  assertEquals(85,FoldMath.defaultCoverMaxAngle(2),0);
 }
 @Test public void aspectFillDoesNotStretch() {
  assertEquals(.5,FoldMath.coverScaleX(2,1),.0001);assertEquals(1,FoldMath.coverScaleY(2,1),.0001);
  assertEquals(1,FoldMath.coverScaleX(.5f,1),.0001);assertEquals(.5,FoldMath.coverScaleY(.5f,1),.0001);
 }
}
