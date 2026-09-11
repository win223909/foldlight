package com.zksaga.foldlight;

/** Reports only observed values: a static reading or a high draw rate is not continuous hinge data. */
final class AngleSamples {
    private int coarseStates;
    private boolean intermediate;
    private int count;

    void reset(){coarseStates=0;intermediate=false;count=0;}
    void add(float angle){
        if(!FoldMath.validAngle(angle))return;
        count++;
        int state=Math.round(angle/90f);
        if(Math.abs(angle-state*90f)<=.5f)coarseStates|=1<<state;
        else intermediate=true;
    }
    boolean coarseOnly(){return !intermediate&&Integer.bitCount(coarseStates)>=2;}
    String description(){
        if(count==0)return "等待角度数据。此模式仅按传感器读数驱动，不会自行播放动画。";
        if(coarseOnly())return "目前只收到 0° / 90° / 180° 中的离散状态，尚未读到连续角度。";
        if(intermediate)return "已收到中间角度值；是否持续跟随开合，需要结合实测确认。";
        return "目前仅收到一个角度状态；静止读数不能证明是否支持连续角度。";
    }
}
