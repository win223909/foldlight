package com.zksaga.foldlight.globaldemo;
import org.junit.Test;
import static org.junit.Assert.*;

public class DisplayContinuityTest {
    @Test public void briefClosedPanelLossKeepsTheSameSession(){
        DisplayContinuity c=new DisplayContinuity();
        assertTrue(c.retain(true,true,0));
        assertTrue(c.retain(false,true,100));
        assertTrue(c.retain(false,true,250));
        assertTrue(c.waiting());
        assertTrue(c.retain(true,true,400));
        assertFalse(c.waiting());
    }
    @Test public void repeatedMissingSamplesCannotExtendDeadline(){
        DisplayContinuity c=new DisplayContinuity();
        assertTrue(c.retain(false,true,100));
        assertTrue(c.retain(false,true,849));
        assertFalse(c.retain(false,true,850));
        assertFalse(c.retain(false,true,1200));
    }
    @Test public void LossOutsideCoveredClosingIsNotTolerated(){
        DisplayContinuity c=new DisplayContinuity();
        assertFalse(c.retain(false,false,100));
        assertTrue(c.retain(false,true,200));
        assertFalse(c.retain(false,false,250));
        assertFalse(c.waiting());
    }
    @Test public void StopResetsThePreviousRecovery(){
        DisplayContinuity c=new DisplayContinuity();
        c.retain(false,true,0);c.reset();
        assertFalse(c.waiting());
        assertFalse(c.retain(false,false,1000));
    }
}
