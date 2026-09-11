package com.zksaga.foldlight;
import org.junit.Test;
import static org.junit.Assert.*;

public class HingeMotionTest {
    @Test public void tinyAndFastSamplesDriveTheStartWithoutAOneDegreeDeadZone(){
        HingeMotion m=new HingeMotion();m.sample(0,1_000_000_000L);m.sample(.5f,1_010_000_000L);
        assertTrue(m.target(1_050_000_000L)>.5f);
        m.sample(1,1_060_000_000L);assertTrue(m.target(1_100_000_000L)>1);
    }
    @Test public void fillsSparseMovementButCannotKeepDriftingWhileStopped(){
        HingeMotion m=new HingeMotion();m.sample(40,1_000_000_000L);m.sample(50,1_500_000_000L);
        assertTrue(m.target(1_750_000_000L)>50);
        assertTrue(m.target(5_000_000_000L)<=60);
        assertEquals(m.target(3_000_000_000L),m.target(5_000_000_000L),0);
    }
    @Test public void reversalChangesDirectionAndStaleSamplesAreIgnored(){
        HingeMotion m=new HingeMotion();m.sample(80,1_000_000_000L);m.sample(90,1_200_000_000L);m.sample(78,1_400_000_000L);
        float target=m.target(1_500_000_000L);assertTrue(target<78);
        m.sample(130,1_300_000_000L);assertEquals(target,m.target(1_500_000_000L),0);
    }
    @Test public void longPauseAndEndpointsStayBounded(){
        HingeMotion m=new HingeMotion();m.sample(170,1_000_000_000L);m.sample(180,1_200_000_000L);
        assertEquals(180,m.target(2_000_000_000L),.1);
        m.sample(60,3_000_000_000L);assertEquals(60,m.target(4_000_000_000L),0);
        assertEquals(180,FoldMath.coverHinge(0),0);assertEquals(0,FoldMath.coverHinge(180),0);
        assertTrue(FoldMath.coverHinge(20)<180);
    }
    @Test public void incomingSampleJoinsPreviousTrajectoryWithoutPositionJump(){
        HingeMotion m=new HingeMotion();m.sample(30,1_000_000_000L);
        m.sample(40,1_300_000_000L);
        long next=1_600_000_000L;float before=m.target(next);
        m.sample(50,next);
        assertEquals(before,m.target(next),.001);
        assertTrue(m.target(next+100_000_000L)>before);
        assertTrue(Math.abs(m.target(next+5_000_000_000L)-50)<=10);
    }
}
