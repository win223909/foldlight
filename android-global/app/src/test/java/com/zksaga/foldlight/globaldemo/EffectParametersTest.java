package com.zksaga.foldlight.globaldemo;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class EffectParametersTest {
    @Test public void UpgradePreservesExistingGlobalAppearance(){
        EffectParameters s=EffectParameters.read(Collections.emptyMap());
        int[] expected={80,90,55,70,60,55};
        for(EffectParameters.Control c:EffectParameters.Control.values())assertEquals(expected[c.ordinal()],s.progress(c));
    }
    @Test public void EachStoredControlIsIndependent(){
        Map<String,Object> saved=new HashMap<>();
        saved.put("darkStart",115f);saved.put("lightStart",150f);saved.put("innerLightStart",33f);saved.put("innerDarkStart",88f);saved.put("edgeDeformation",.24f);saved.put("simpleBlur",.72f);
        EffectParameters s=EffectParameters.read(saved);int[] expected={115,150,33,88,24,72};
        for(EffectParameters.Control c:EffectParameters.Control.values())assertEquals(expected[c.ordinal()],s.progress(c));
        saved.put("simpleBlur",0f);assertEquals(72,s.progress(EffectParameters.Control.BLUR));
    }
    @Test public void BadPersistedValuesCannotReachShaders(){
        Map<String,Object> saved=new HashMap<>();saved.put("darkStart",Float.NaN);saved.put("lightStart",200f);saved.put("innerLightStart",-80f);saved.put("innerDarkStart","bad");saved.put("edgeDeformation",Float.POSITIVE_INFINITY);saved.put("simpleBlur",-1f);
        EffectParameters s=EffectParameters.read(saved);int[] expected={80,180,0,70,60,0};
        for(EffectParameters.Control c:EffectParameters.Control.values())assertEquals(expected[c.ordinal()],s.progress(c));
    }
    @Test public void SliderPercentagesRoundTripAtEveryStep(){
        for(EffectParameters.Control c:EffectParameters.Control.values())for(int i=c.min;i<=c.max;i++)assertEquals(i,EffectParameters.read(Collections.singletonMap(c.key,c.stored(i))).progress(c));
    }
}
