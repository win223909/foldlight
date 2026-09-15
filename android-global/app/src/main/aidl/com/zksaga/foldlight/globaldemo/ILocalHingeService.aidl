package com.zksaga.foldlight.globaldemo;
import com.zksaga.foldlight.globaldemo.ILocalHingeCallback;
import android.os.Bundle;
import android.view.SurfaceControl;
interface ILocalHingeService {
    void start(ILocalHingeCallback callback) = 1;
    oneway void heartbeat(boolean sampling, boolean dualAllowed) = 2;
    oneway void stop() = 3;
    Bundle sessionState() = 4;
    Bundle moveTask(int source, int destination) = 5;
    Bundle captureBehind(int displayId, in SurfaceControl[] excluded) = 6;
    Bundle windowState(int displayId) = 7;
    void backOnInner() = 8;
    void destroy() = 16777114;
}
