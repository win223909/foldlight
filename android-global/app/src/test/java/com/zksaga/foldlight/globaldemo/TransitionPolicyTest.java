package com.zksaga.foldlight.globaldemo;
import org.junit.Test;import static org.junit.Assert.*;
public class TransitionPolicyTest {
 @Test public void openingJitterDoesNotMoveTasksBackAndForth(){TransitionPolicy p=new TransitionPolicy(false);assertEquals(1,p.update(5,0));assertEquals(0,p.update(70,100));assertEquals(0,p.update(65,150));assertEquals(0,p.update(68,200));assertEquals(-1,p.update(58,250));assertEquals(0,p.update(60,300));}
 @Test public void fullCycleNeedsStableEndpoints(){TransitionPolicy p=new TransitionPolicy(false);assertEquals(1,p.update(10,0));assertEquals(0,p.update(180,100));assertEquals(2,p.update(180,270));assertEquals(-1,p.update(170,300));assertEquals(0,p.update(0,500));assertEquals(2,p.update(0,670));assertEquals(1,p.update(10,700));}
 @Test public void endpointSpikeMustNotFinish(){TransitionPolicy p=new TransitionPolicy(false);p.update(30,0);assertEquals(0,p.update(180,100));assertEquals(0,p.update(175,200));assertEquals(0,p.update(180,300));assertEquals(0,p.update(180,420));assertEquals(2,p.update(180,470));}
 @Test public void coarseJumpDoesNotInventAnimation(){TransitionPolicy p=new TransitionPolicy(false);assertEquals(0,p.update(180,0));assertFalse(p.active);assertEquals(0,p.update(Float.NaN,100));assertEquals(0,p.update(181,100));assertEquals(-1,p.update(100,200));}
}
