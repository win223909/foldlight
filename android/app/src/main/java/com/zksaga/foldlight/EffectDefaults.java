package com.zksaga.foldlight;

import android.content.SharedPreferences;

final class EffectDefaults {
    static final float INNER=.9f,COVER=100f,SOFTNESS=.75f,FROST=.20f,DARK_START=85f,LIGHT_START=90f,INNER_LIGHT_START=90f,INNER_DARK_START=180f;
    private EffectDefaults(){}
    /** New controls receive defaults; every existing adjustment survives an upgrade. */
    static void migrate(SharedPreferences settings){
        if(settings.getInt("effectDefaultsRevision",0)>=4)return;
        SharedPreferences.Editor edit=settings.edit();
        String[] keys={"innerStrength","coverMaxAngle","softness","frost","darkStart","lightStart","innerLightStart","innerDarkStart"};
        float[] values={INNER,COVER,SOFTNESS,FROST,DARK_START,LIGHT_START,INNER_LIGHT_START,INNER_DARK_START};
        for(int i=0;i<keys.length;i++)if(!settings.contains(keys[i]))edit.putFloat(keys[i],values[i]);
        if(!settings.contains("coverDimming"))edit.putBoolean("coverDimming",true);
        edit.putInt("glassTransitionRevision",1).putInt("effectDefaultsRevision",4).apply();
    }
}
