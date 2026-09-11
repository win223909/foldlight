package com.zksaga.foldlight;

import android.content.SharedPreferences;

final class MotionSettings {
    static MotionProfile[] load(SharedPreferences prefs){
        MotionProfile[] result=MotionProfile.defaults();
        for(int i=0;i<result.length;i++){
            String k=MotionProfile.KEYS[i];MotionProfile d=result[i];
            result[i]=new MotionProfile(prefs.getFloat(k+"Start",d.start),prefs.getFloat(k+"Speed",1),prefs.getFloat(k+"Amplitude",1),prefs.getFloat(k+"Acceleration",1));
        }
        return result;
    }
    static void save(SharedPreferences.Editor edit,MotionProfile[] profiles){
        for(int i=0;i<profiles.length;i++){
            String k=MotionProfile.KEYS[i];MotionProfile p=profiles[i];
            edit.putFloat(k+"Start",p.start).putFloat(k+"Speed",p.speed).putFloat(k+"Amplitude",p.amplitude).putFloat(k+"Acceleration",p.acceleration);
        }
    }
}
