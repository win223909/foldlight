package com.zksaga.foldlight.globaldemo;
import org.junit.Test;
import static org.junit.Assert.*;
public class CaptureGateTest {
    @Test public void switchingPanelRejectsOldScreenshot(){CaptureGate g=new CaptureGate();int a=g.begin(1000);g.invalidate();int b=g.begin(1400);assertFalse(g.complete(a));assertTrue(g.complete(b));}
    @Test public void pauseRejectsPendingCallback(){CaptureGate g=new CaptureGate();int a=g.begin(1000);g.invalidate();assertFalse(g.accepts(a));}
    @Test public void throttleSurvivesDisplayChanges(){CaptureGate g=new CaptureGate();g.begin(1000);g.invalidate();assertEquals(-1,g.begin(1100));assertEquals(250,g.delay(1100));assertTrue(g.begin(1350)>0);}
    @Test public void duplicateCallbackCannotReplaceNewFrame(){CaptureGate g=new CaptureGate();int a=g.begin(1000);assertTrue(g.complete(a));assertFalse(g.complete(a));int b=g.begin(1400);assertFalse(g.complete(a));assertTrue(g.complete(b));}
    @Test public void pendingRequestCannotBeOverwritten(){CaptureGate g=new CaptureGate();int a=g.begin(1000);assertEquals(-1,g.begin(2000));assertTrue(g.accepts(a));}
}
