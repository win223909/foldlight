package com.zksaga.foldlight;

/** Critically damped visual follow: continuous velocity when a sparse measurement changes target. */
final class FrameMotion {
    private float velocity;
    void reset(){velocity=0;}
    float step(float current,float target,float seconds){return step(current,target,seconds,.7f);}
    float step(float current,float target,float seconds,float softness){
        float dt=FoldMath.clamp(seconds,0,.05f),omega=60-42*FoldMath.clamp(softness,0,1);
        // Reduce start-up lag near fully closed without changing the chosen mid-travel softness.
        float early=1-FoldMath.ease(Math.max(current,target)/25);omega*=1+.35f*early;
        float offset=current-target,j=velocity+omega*offset;
        float decay=(float)Math.exp(-omega*dt);
        float next=target+(offset+j*dt)*decay;
        velocity=(velocity-omega*j*dt)*decay;
        if(next<0||next>180){next=FoldMath.clamp(next,0,180);velocity=0;}
        if(Math.abs(next-target)<.005f&&Math.abs(velocity)<.05f){velocity=0;return target;}
        return next;
    }
}
