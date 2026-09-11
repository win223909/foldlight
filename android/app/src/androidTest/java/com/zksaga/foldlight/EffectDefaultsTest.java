package com.zksaga.foldlight;

import android.content.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class EffectDefaultsTest {
    @Test public void newInstallGetsRequestedDefaults(){
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        String name="test-effect-defaults-"+System.nanoTime();SharedPreferences prefs=context.getSharedPreferences(name,0);
        try{
            EffectDefaults.migrate(prefs);
            assertEquals(.9f,prefs.getFloat("innerStrength",0),0);assertEquals(100,prefs.getFloat("coverMaxAngle",0),0);
            assertEquals(.6f,prefs.getFloat("edgeDeformation",0),0);assertEquals(1f,prefs.getFloat("simpleBlur",0),0);assertEquals(.75f,prefs.getFloat("softness",0),0);assertEquals(.2f,prefs.getFloat("frost",0),0);
            assertTrue(prefs.getBoolean("coverDimming",false));assertEquals(80,prefs.getFloat("darkStart",0),0);assertEquals(90,prefs.getFloat("lightStart",0),0);
            assertEquals(55,prefs.getFloat("innerLightStart",0),0);assertEquals(70,prefs.getFloat("innerDarkStart",0),0);
        }finally{context.deleteSharedPreferences(name);}
    }
    @Test public void restoreOnlyChangesSixParametersAndSurvivesReload(){
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();String name="test-restore-"+System.nanoTime();
        SharedPreferences prefs=context.getSharedPreferences(name,0);
        try{
            prefs.edit().putFloat("darkStart",120).putFloat("simpleBlur",.1f).putString("picture","user-photo").putString("mode","sensor").commit();
            EffectDefaults.restore(prefs);
            assertEquals(80,prefs.getFloat("darkStart",0),0);assertEquals(90,prefs.getFloat("lightStart",0),0);
            assertEquals(55,prefs.getFloat("innerLightStart",0),0);assertEquals(70,prefs.getFloat("innerDarkStart",0),0);
            assertEquals(.6f,prefs.getFloat("edgeDeformation",0),0);assertEquals(1,prefs.getFloat("simpleBlur",0),0);
            assertEquals("user-photo",prefs.getString("picture",null));assertEquals("sensor",prefs.getString("mode",null));
            EffectDefaults.migrate(prefs);assertEquals(1,prefs.getFloat("simpleBlur",0),0);
        }finally{context.deleteSharedPreferences(name);}
    }
    @Test public void upgradesOnlyAddMissingControlsAndPreserveCustomizations(){
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        String name="test-effect-upgrade-"+System.nanoTime();SharedPreferences prefs=context.getSharedPreferences(name,0);
        try{
            prefs.edit().putInt("effectDefaultsRevision",1).putFloat("innerStrength",.8f).putFloat("coverMaxAngle",60).putFloat("darkStart",85).putFloat("lightStart",80).putString("mode","manual").commit();
            EffectDefaults.migrate(prefs);
            assertEquals(.8f,prefs.getFloat("innerStrength",0),0);assertEquals(60,prefs.getFloat("coverMaxAngle",0),0);
            assertEquals(85,prefs.getFloat("darkStart",0),0);assertEquals(80,prefs.getFloat("lightStart",0),0);assertEquals("manual",prefs.getString("mode",null));
            assertEquals(55,prefs.getFloat("innerLightStart",0),0);assertEquals(70,prefs.getFloat("innerDarkStart",0),0);
            prefs.edit().putFloat("innerLightStart",55).putFloat("innerDarkStart",145).commit();EffectDefaults.migrate(prefs);
            assertEquals(55,prefs.getFloat("innerLightStart",0),0);assertEquals(145,prefs.getFloat("innerDarkStart",0),0);
        }finally{context.deleteSharedPreferences(name);}
    }
}
