package com.zksaga.foldlight.globaldemo;
import org.junit.Test;
import static org.junit.Assert.*;
public class InnerRevealTest {
 @Test public void newInnerBecomesBrightBeforeFullUnfold(){
  InnerDimming b=new InnerDimming();b.step(0,55,70,120);
  assertTrue(b.step(90,55,70,120)>.5f);
  assertEquals(1f,b.step(120,55,70,120),.001f);
  assertEquals(1f,b.step(160,55,70,120),.001f);
 }
 @Test public void reverseDirectionDoesNotDarkenAboveClosingThreshold(){
  InnerDimming b=new InnerDimming();b.step(0,55,70,120);
  float previous=b.step(100,55,70,120);
  assertEquals(previous,b.step(99,55,70,120),.001f);
  b.step(180,55,70,120);
  assertEquals(1f,b.step(80,55,70,120),.001f);
  assertEquals(0f,b.step(0,55,70,120),.001f);
 }
}
