package com.zksaga.foldlight.globaldemo;

import java.util.Map;

/** Immutable rendering snapshot. Persistent settings are never read inside a frame. */
final class EffectParameters {
    static final String STORE="global_effect_parameters";
    enum Control {
        COVER_DARK("darkStart","打开开始变暗角度",40,179,80,1),
        COVER_LIGHT("lightStart","折叠开始变亮角度",41,180,90,1),
        INNER_LIGHT("innerLightStart","打开开始变亮角度",0,179,55,1),
        INNER_DARK("innerDarkStart","折叠开始变暗角度",1,180,70,1),
        EDGE("edgeDeformation","外屏右侧压缩程度",0,100,60,100),
        BLUR("simpleBlur","模糊程度",0,100,55,100);
        final String key,title;final int min,max,recommended,scale;
        Control(String key,String title,int min,int max,int recommended,int scale){this.key=key;this.title=title;this.min=min;this.max=max;this.recommended=recommended;this.scale=scale;}
        String format(int value){return value+(scale==1?"°":"%");}
        float stored(int value){return Math.max(min,Math.min(max,value))/(float)scale;}
    }
    private final float[] values;
    private EffectParameters(float[] values){this.values=values;}
    static EffectParameters read(Map<String,?> saved){
        float[] values=new float[Control.values().length];
        for(Control c:Control.values()){
            Object raw=saved.get(c.key);float v=raw instanceof Number?((Number)raw).floatValue():c.recommended/(float)c.scale;
            if(!Float.isFinite(v))v=c.recommended/(float)c.scale;
            values[c.ordinal()]=Math.max(c.min/(float)c.scale,Math.min(c.max/(float)c.scale,v));
        }
        return new EffectParameters(values);
    }
    float value(Control c){return values[c.ordinal()];}
    int progress(Control c){return Math.round(value(c)*c.scale);}
}
