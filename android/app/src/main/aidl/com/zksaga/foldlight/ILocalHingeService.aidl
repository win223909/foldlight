package com.zksaga.foldlight;
import com.zksaga.foldlight.ILocalHingeCallback;
interface ILocalHingeService {
    void start(ILocalHingeCallback callback) = 1;
    oneway void heartbeat(boolean sampling, boolean dualAllowed) = 2;
    oneway void stop() = 3;
    void destroy() = 16777114;
}
