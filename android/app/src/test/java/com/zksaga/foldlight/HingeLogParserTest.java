package com.zksaga.foldlight;
import org.junit.Test;
import static org.junit.Assert.*;

public class HingeLogParserTest {
    @Test public void parsesActualFoldAndFusionFieldsInsteadOfPosture(){
        HingeLogParser.Sample a=HingeLogParser.parse("I/sensors-hal(2040): handle_sns_client_event:107, [0]folding_angle ts=775319289381972 ns value [ 97/0] ([0/0/2/2])");
        assertNotNull(a);assertEquals(97,a.angle,0);assertEquals(775319289381972L,a.nanos);
        HingeLogParser.Sample b=HingeLogParser.parse("I/sensors-hal(2040): handle_sns_client_event:197, [0]lid_angle_fusion ts=775319409459993 ns value [1/ 69/3] [69/3]");
        assertNotNull(b);assertEquals(69,b.angle,0);
    }
    @Test public void ignoresMetadataOtherSensorsAndInvalidValues(){
        assertNull(HingeLogParser.parse("folding_angle skip same angle 2/2"));
        assertNull(HingeLogParser.parse("handle_sns_client_event:50, hinge_angle ts=775319244203951 ns value 90/138/0"));
        assertNull(HingeLogParser.parse("handle_sns_client_event:107, [0]folding_angle ts=123 ns value [999/0]"));
    }
    @Test public void replayedOldLogsAndFutureSamplesCannotDriveTheRenderer(){
        long now=10_000_000_000L;
        assertTrue(HingeLogParser.fresh(now-30_000_000L,now));
        assertFalse(HingeLogParser.fresh(now-501_000_000L,now));
        assertFalse(HingeLogParser.fresh(now+51_000_000L,now));assertFalse(HingeLogParser.fresh(0,now));
    }
}
