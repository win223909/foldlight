package com.zksaga.foldlight.globaldemo;

/** Epochs reject screenshots which arrive after a panel switch, pause or timeout. */
final class CaptureGate {
    private int epoch;
    private boolean pending;
    private long lastRequest=-1000;
    int begin(long now){if(pending||now-lastRequest<350)return -1;pending=true;lastRequest=now;return ++epoch;}
    boolean accepts(int ticket){return pending&&ticket==epoch;}
    boolean complete(int ticket){if(!accepts(ticket))return false;pending=false;return true;}
    void invalidate(){epoch++;pending=false;}
    boolean pending(){return pending;}
    long delay(long now){return Math.max(0,350-(now-lastRequest));}
}
