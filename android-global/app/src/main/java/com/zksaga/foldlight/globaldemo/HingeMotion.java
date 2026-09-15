package com.zksaga.foldlight.globaldemo;

/** Bounded visual prediction for sparse angle logs. Raw measurements remain unchanged. */
final class HingeMotion {
    private long stamp;
    private float raw=180,velocity,correction,joinSeconds=.09f;
    void reset(){stamp=0;raw=180;velocity=0;correction=0;}
    void sample(float angle,long nanos){
        if(!FoldMath.validAngle(angle)||nanos<=stamp)return;
        float previous=target(nanos);
        joinSeconds=Math.max(angle,raw)<15?.03f:.09f;
        correction=0;
        if(stamp!=0){
            float dt=(nanos-stamp)/1e9f,delta=angle-raw;
            if(dt>.75f)velocity=0;
            else if(dt>=.001f){
                float observed=Math.abs(delta)<.001f?0:FoldMath.clamp(delta/dt,-360,360);
                velocity=velocity*observed<0?observed:velocity*.35f+observed*.65f;
            }
            // Join the previous visual trajectory instead of snapping to each sparse sample.
            if(dt<=.75f)correction=FoldMath.clamp(previous-angle,-10,10);
        }
        if(angle<=.01f||angle>=179.99f)velocity=0;
        raw=angle;stamp=nanos;
    }
    float target(long now){
        float elapsed=stamp==0?0:FoldMath.clamp((now-stamp)/1e9f,0,.55f);
        // Soft saturation avoids the abrupt stop at the old hard eight-degree limit.
        float prediction=10*(float)Math.tanh(velocity*elapsed/10);
        float offset=correction*(float)Math.exp(-elapsed/joinSeconds);
        return FoldMath.clamp(raw+FoldMath.clamp(prediction+offset,-10,10),0,180);
    }
}
