package com.zksaga.foldlight.globaldemo;

/** Direction hysteresis keeps one task transfer per leg, including reversals. */
final class TransitionPolicy {
    boolean opening,active;float extreme;long endpoint=-1;
    TransitionPolicy(boolean inner){opening=inner;extreme=inner?180:0;}
    int update(float a,long now){
        if(!Float.isFinite(a)||a<0||a>180)return 0;
        if(!active){
            if(!opening&&a>3&&a<174){active=true;opening=true;extreme=a;return 1;}
            if(opening&&a<174&&a>3){active=true;opening=false;extreme=a;return -1;}
            if(a>=176)opening=true;if(a<=1)opening=false;return 0;
        }
        if(a>=176||a<=1){if(endpoint<0)endpoint=now;if(now-endpoint>=160){active=false;opening=a>=176;endpoint=-1;return 2;}}
        else endpoint=-1;
        if(opening){extreme=Math.max(extreme,a);if(extreme-a>=12){opening=false;extreme=a;return -1;}}
        else{extreme=Math.min(extreme,a);if(a-extreme>=12){opening=true;extreme=a;return 1;}}
        return 0;
    }
}
