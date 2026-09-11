package com.zksaga.foldlight;

/** Independent inner-pane brightness envelopes; reversals keep the current glass state. */
final class InnerDimming {
    private float level=Float.NaN;
    float step(float angle,float openingStart,float closingStart){
        if(!FoldMath.validAngle(angle))return Float.isNaN(level)?0:level;
        openingStart=FoldMath.clamp(openingStart,0,179);
        closingStart=FoldMath.clamp(closingStart,1,180);
        float closingEnd=closingEnd(openingStart,closingStart);
        float opening=FoldMath.clamp((angle-openingStart)/(180-openingStart),0,1);
        float closing=FoldMath.clamp((angle-closingEnd)/(closingStart-closingEnd),0,1);
        // The closing envelope is always above the opening envelope. This avoids
        // brightness jumps when direction reverses or either threshold changes.
        level=Float.isNaN(level)?opening:FoldMath.clamp(level,opening,closing);
        return level;
    }
    static float closingEnd(float openingStart,float closingStart){
        return Math.min(FoldMath.clamp(openingStart,0,179),Math.max(0,FoldMath.clamp(closingStart,1,180)-90));
    }
    float value(){return level;}
    void restore(float value){level=Float.isNaN(value)?value:FoldMath.clamp(value,0,1);}
}
