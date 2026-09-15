package com.zksaga.foldlight.globaldemo;

import android.content.SharedPreferences;

final class EffectDefaults {
    static final float INNER=.9f,COVER=100f,SOFTNESS=.75f,FROST=.20f,DARK_START=80f,LIGHT_START=90f,INNER_LIGHT_START=55f,INNER_DARK_START=70f,EDGE=.6f,SIMPLE_BLUR=1f;
    private EffectDefaults(){}
    static void restore(SharedPreferences prefs){
        prefs.edit().putFloat("darkStart",DARK_START).putFloat("lightStart",LIGHT_START)
            .putFloat("innerLightStart",INNER_LIGHT_START).putFloat("innerDarkStart",INNER_DARK_START)
            .putFloat("edgeDeformation",EDGE).putFloat("simpleBlur",SIMPLE_BLUR).apply();
    }
    /** New controls receive defaults; every existing adjustment survives an upgrade. */
    static void migrate(SharedPreferences settings){
        if(settings.getInt("effectDefaultsRevision",0)>=5)return;
        SharedPreferences.Editor edit=settings.edit();
        String[] keys={"innerStrength","coverMaxAngle","softness","frost","darkStart","lightStart","innerLightStart","innerDarkStart","edgeDeformation","simpleBlur"};
        float[] values={INNER,COVER,SOFTNESS,FROST,DARK_START,LIGHT_START,INNER_LIGHT_START,INNER_DARK_START,EDGE,settings.getFloat("coverFrost",settings.getFloat("frost",SIMPLE_BLUR))};
        for(int i=0;i<keys.length;i++)if(!settings.contains(keys[i]))edit.putFloat(keys[i],values[i]);
        if(!settings.contains("coverDimming"))edit.putBoolean("coverDimming",true);
        edit.putInt("glassTransitionRevision",1).putInt("effectDefaultsRevision",5).apply();
    }
}
