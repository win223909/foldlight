package com.zksaga.foldlight;

/** Exact devices/panels measured for local hinge and concurrent-display testing. */
public final class FoldDeviceProfile {
    private static final FoldDeviceProfile FOLD8=new FoldDeviceProfile(1248,1972,2448,1848);
    private static final FoldDeviceProfile FOLD7=new FoldDeviceProfile(1080,2520,1968,2184);
    public final int coverWidth,coverHeight,innerWidth,innerHeight;
    public final int secondaryDisplayId=1,concurrentOuterState=5;
    private FoldDeviceProfile(int coverWidth,int coverHeight,int innerWidth,int innerHeight){
        this.coverWidth=coverWidth;this.coverHeight=coverHeight;this.innerWidth=innerWidth;this.innerHeight=innerHeight;
    }
    public static FoldDeviceProfile forModel(String model){
        if("SM-F9710".equals(model))return FOLD8;
        if("SM-F9660".equals(model))return FOLD7;
        return null;
    }
    private static boolean matches(int width,int height,int expectedWidth,int expectedHeight){
        return (width==expectedWidth&&height==expectedHeight)||(width==expectedHeight&&height==expectedWidth);
    }
    public boolean isCover(int width,int height){return matches(width,height,coverWidth,coverHeight);}
    public boolean isInner(int width,int height){return matches(width,height,innerWidth,innerHeight);}
    public boolean isSecondaryPanel(int displayId,int width,int height){
        return displayId==secondaryDisplayId&&(isCover(width,height)||isInner(width,height));
    }
    public int width(boolean cover){return cover?coverWidth:innerWidth;}
    public int height(boolean cover){return cover?coverHeight:innerHeight;}
}
