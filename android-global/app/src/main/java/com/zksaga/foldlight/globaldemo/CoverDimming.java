package com.zksaga.foldlight.globaldemo;

/** Physical hinge angle, independent of the user's maximum picture rotation.
 * Two envelopes preserve brightness on reversals instead of snapping between curves. */
final class CoverDimming {
    private float level=Float.NaN;
    float step(float angle){return step(angle,85,90);}
    float step(float angle,float darkStart,float lightStart){
        if(!FoldMath.validAngle(angle))return Float.isNaN(level)?1:level;
        // Keep a nonzero fade span and the common fully-bright endpoint at 40 degrees.
        darkStart=FoldMath.clamp(darkStart,40,179);lightStart=FoldMath.clamp(lightStart,41,180);
        float opening=1-smooth((angle-darkStart)/(180-darkStart));
        float closing=1-smooth((angle-40)/(lightStart-40));
        level=Float.isNaN(level)?opening:FoldMath.clamp(level,closing,opening);
        return level;
    }
    float value(){return level;}
    void restore(float value){level=Float.isNaN(value)?value:FoldMath.clamp(value,0,1);}
    private static float smooth(float x){x=FoldMath.clamp(x,0,1);return x*x*(3-2*x);}
}
