package com.zksaga.foldlight.globaldemo;

/** A bounded grace period for the measured closed-panel reconfiguration. */
final class DisplayContinuity {
    static final long GRACE_MS=750;
    private long missingSince=-1;
    boolean waiting(){return missingSince>=0;}
    boolean retain(boolean ready,boolean eligible,long now){
        if(ready){reset();return true;}
        if(!eligible){reset();return false;}
        if(missingSince<0)missingSince=now;
        return now-missingSince<GRACE_MS;
    }
    void reset(){missingSince=-1;}
}
