package com.zksaga.foldlight;
oneway interface ILocalHingeCallback {
    void angle(long timestamp, float degrees);
    void status(String message);
}
