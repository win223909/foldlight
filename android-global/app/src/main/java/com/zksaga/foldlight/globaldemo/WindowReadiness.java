/* Adapted from bunkaich/Folduo, Copyright (c) 2026 bunkaich. MIT; see licenses/Folduo-MIT.txt. */
package com.zksaga.foldlight.globaldemo;

import java.util.regex.*;

/** Returns geometry and draw state only; window titles, app names and content are discarded. */
final class WindowReadiness {
    record State(boolean ready,String geometry){}
    static State parse(String dump,int displayId){
        for(String window:dump.split("(?m)^  Window #")){
            if(!Pattern.compile("mDisplayId="+displayId+"(?:\\s|$)").matcher(window).find()||!window.contains("ty=BASE_APPLICATION"))continue;
            if(!window.contains("mViewVisibility=0x0")||!window.contains("isOnScreen=true"))continue;
            Matcher frame=Pattern.compile("Frames:.*?frame=(\\[[^\\n]+?) last=").matcher(window);
            String geometry=frame.find()?frame.group(1):"";
            boolean ready=!geometry.isEmpty()&&window.contains("mHasSurface=true")&&window.contains("isReadyForDisplay()=true")
                &&window.contains("shown=true")&&window.contains("mDrawState=HAS_DRAWN")&&window.contains("insetsChanged=false")
                &&!window.contains("mAnimatingExit=true")&&!window.contains("mAppFreezing=true");
            return new State(ready,geometry);
        }
        return new State(false,"");
    }
}
