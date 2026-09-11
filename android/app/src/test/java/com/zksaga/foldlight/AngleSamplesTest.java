package com.zksaga.foldlight;

import org.junit.Test;
import static org.junit.Assert.*;

public class AngleSamplesTest {
    @Test public void repeatedStaticValueIsNotEvidenceOfCoarseSensor(){
        AngleSamples samples=new AngleSamples();
        for(int i=0;i<500;i++)samples.add(90);
        assertFalse(samples.coarseOnly());
    }
    @Test public void threeStateSensorIsReportedAsObservedCoarseData(){
        AngleSamples samples=new AngleSamples();
        for(float angle:new float[]{0,90,180,90,0})samples.add(angle);
        assertTrue(samples.coarseOnly());
        samples.add(123.4f);
        assertFalse(samples.coarseOnly());
    }
    @Test public void InvalidReadingsDoNotSuggestContinuousDataAndSourceResetClearsEvidence(){
        AngleSamples samples=new AngleSamples();samples.add(0);samples.add(180);
        samples.add(Float.NaN);samples.add(-9);samples.add(360);
        assertTrue(samples.coarseOnly());samples.reset();assertFalse(samples.coarseOnly());
    }
}
