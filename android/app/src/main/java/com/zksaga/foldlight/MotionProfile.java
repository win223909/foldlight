package com.zksaga.foldlight;

/** Four independent gesture profiles. Speed is temporal response; acceleration shapes travel. */
final class MotionProfile {
    static final String[] KEYS={"coverOpen","coverClose","innerOpen","innerClose"};
    float start,speed,amplitude,acceleration;
    MotionProfile(float start,float speed,float amplitude,float acceleration){
        this.start=FoldMath.clamp(start,0,180);this.speed=FoldMath.clamp(speed,.25f,4);
        this.amplitude=FoldMath.clamp(amplitude,0,2);this.acceleration=FoldMath.clamp(acceleration,.25f,3);
    }
    static MotionProfile[] defaults(){return new MotionProfile[]{new MotionProfile(0,1,1,1),new MotionProfile(180,1,1,1),new MotionProfile(0,1,1,1),new MotionProfile(180,1,1,1)};}
}
