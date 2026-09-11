package com.zksaga.foldlight;

/** A continuous, anchored gesture: changing direction never swaps to an unrelated pose. */
final class DeformationMotion {
    private final boolean cover;
    private float value=Float.NaN,extreme,lastAngle,anchor,start;
    private boolean opening=true;
    DeformationMotion(boolean cover){this.cover=cover;}
    float value(){return value;}
    float[] state(){return new float[]{value,extreme,lastAngle,anchor,start,opening?1:0};}
    void restore(float[] state){if(state!=null&&state.length==6){value=state[0];extreme=state[1];lastAngle=state[2];anchor=state[3];start=state[4];opening=state[5]==1;}}
    void reanchor(MotionProfile open,MotionProfile close){if(Float.isFinite(value))begin(lastAngle,opening,opening?open:close);}
    private void begin(float angle,boolean direction,MotionProfile profile){
        opening=direction;extreme=angle;anchor=value;
        start=opening?Math.max(angle,profile.start):Math.min(angle,profile.start);
    }
    float step(float angle,float dt,float maximum,float strength,MotionProfile open,MotionProfile close){
        if(!FoldMath.validAngle(angle))return Float.isFinite(value)?value:0;
        angle=FoldMath.clamp(angle,0,180);
        float limit=cover?180:85;
        float base=cover?FoldMath.clamp(maximum,0,180):FoldMath.panelTilt(0,strength);
        if(!Float.isFinite(value)){
            value=cover?FoldMath.coverTilt(angle,maximum):FoldMath.panelTilt(angle,strength);
            if(angle<=.001f)value=cover?0:Math.min(limit,base*close.amplitude);
            if(angle>=179.999f)value=cover?Math.min(limit,base*open.amplitude):0;
            lastAngle=angle;begin(angle,angle<180,angle<180?open:close);
        }
        // Direction hysteresis rejects stationary sensor jitter, without waiting for coarse fold states.
        if(opening&&angle<extreme-.25f)begin(lastAngle,false,close);
        else if(!opening&&angle>extreme+.25f)begin(lastAngle,true,open);
        extreme=opening?Math.max(extreme,angle):Math.min(extreme,angle);lastAngle=angle;
        MotionProfile profile=opening?open:close;
        float travel=opening?180-start:start;
        float p=travel<=0?0:FoldMath.clamp((opening?angle-start:start-angle)/travel,0,1);
        float target=anchor;
        if(profile.amplitude>0&&p>0){
            float shaped=(float)Math.pow(p,profile.acceleration);
            boolean returning=cover?!opening:opening;
            // On a return-to-flat leg, amplitude controls how much of the remaining bend
            // is removed per unit travel. Every nonzero amplitude still returns fully flat.
            if(returning)shaped=1-(float)Math.pow(1-shaped,profile.amplitude);
            float curve;
            if(cover)curve=opening?FoldMath.coverTilt(shaped*180,1):1-FoldMath.coverTilt((1-shaped)*180,1);
            else curve=base<.0001f?shaped:opening?1-FoldMath.panelTilt(shaped*180,strength)/base:FoldMath.panelTilt((1-shaped)*180,strength)/base;
            float end=returning?0:Math.min(limit,base*profile.amplitude);
            target=anchor+(end-anchor)*FoldMath.clamp(curve,0,1);
        }
        float seconds=Float.isFinite(dt)?FoldMath.clamp(dt,0,.05f):0;
        value+=(target-value)*(1-(float)Math.exp(-seconds/(.018f/profile.speed)));
        if(Math.abs(target-value)<.0001f)value=target;
        return value=FoldMath.clamp(value,0,limit);
    }
}
