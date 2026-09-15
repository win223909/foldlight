package com.zksaga.foldlight;

/** Pure motion math, independent of the sensor and Android lifecycle. */
public final class FoldMath {
    private FoldMath() {}
    public static float clamp(float x,float min,float max) { return Math.max(min,Math.min(max,x)); }
    public static boolean validAngle(float angle) { return Float.isFinite(angle) && angle>=0 && angle<=180.5f; }
    public static float ease(float progress) { float p=clamp(progress,0,1);return p*p*(3-2*p); }
    public static float panelTilt(float hinge) { return panelTilt(hinge,1); }
    public static float panelTilt(float hinge,float strength) {
        // A continuous shoulder replaces the old 85-degree hard stop halfway through folding.
        float amount=clamp(strength,.25f,2.5f);
        float gain=amount*90/85;
        float progress=(180-clamp(hinge,0,180))/180;
        return 85*Math.min(amount,1)*(float)(Math.tanh(gain*progress)/Math.tanh(gain));
    }
    // Use the entire physical travel; the former 60-degree endpoint froze the cover early.
    public static float coverHinge(float physicalAngle) { return 180-clamp(physicalAngle,0,180); }
    /** Maximum visual rotation is independent of the physical hinge travel. */
    public static float coverTilt(float physicalAngle,float maximumDegrees) {
        float p=clamp(physicalAngle,0,180)/180;
        // A gentle early gain makes the first few degrees visible; endpoints and monotonicity stay exact.
        float shaped=p+2.2f*p*(float)Math.pow(1-p,8);
        return clamp(maximumDegrees,0,180)*shaped;
    }
    public static float defaultCoverMaxAngle(float legacyStrength) {
        return Math.round(85*Math.min(clamp(legacyStrength,.25f,2.5f),1));
    }
    public static boolean isCoverSurface(int width,int height) {
        return Math.min(width,height)==1248&&Math.max(width,height)==1972;
    }
    public static boolean isCoverSurface(int width,int height,String model) {
        FoldDeviceProfile profile=FoldDeviceProfile.forModel(model);
        return profile!=null&&profile.isCover(width,height);
    }
    public static float smooth(float current,float target,float seconds) {
        float dt=clamp(seconds,0,.05f);
        float next=current+(target-current)*(1-(float)Math.exp(-dt/.045f));
        return Math.abs(next-target)<.025f ? target : next;
    }
    public static float coverScaleX(float imageAspect,float viewAspect) { return imageAspect>viewAspect ? viewAspect/imageAspect : 1; }
    public static float coverScaleY(float imageAspect,float viewAspect) { return imageAspect>viewAspect ? 1 : imageAspect/viewAspect; }
}
