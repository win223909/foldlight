package com.zksaga.foldlight.globaldemo;
oneway interface ILocalHingeCallback {
    void angle(long timestamp, float degrees);
    void status(String message);
}
