#include <metal_stdlib>
using namespace metal;
struct Output { float4 position [[position]]; };
struct Uniforms { float4 viewport; float4 effect; };
vertex Output foldVertex(uint id [[vertex_id]]) {
    const float2 p[]={float2(-1,-1),float2(3,-1),float2(-1,3)};
    return {float4(p[id],0,1)};
}
// A Gaussian kernel at subpixel through one-source-pixel radius. Combining
// adjacent taps with the linear sampler evaluates a separable 5x5 kernel in
// nine fetches, avoiding the sharp/one-large-blur crossfade at the focus plane.
half3 fineGaussian(texture2d<half> image, float2 uv, float sigma) {
    constexpr sampler s(coord::normalized,address::clamp_to_edge,filter::linear);
    if(sigma<0.08) return image.sample(s,uv).rgb;
    float variance=sigma*sigma;
    float w1=exp(-0.5/variance),w2=exp(-2.0/variance),pair=w1+w2;
    float offset=(w1+2.0*w2)/max(pair,1e-8),center=1.0/(1.0+2.0*pair);
    float side=pair*center;
    float2 d=offset/float2(image.get_width(),image.get_height());
    half3 pixel=image.sample(s,uv).rgb*half(center*center);
    pixel+=(image.sample(s,uv+float2(d.x,0)).rgb+image.sample(s,uv-float2(d.x,0)).rgb
           +image.sample(s,uv+float2(0,d.y)).rgb+image.sample(s,uv-float2(0,d.y)).rgb)*half(center*side);
    pixel+=(image.sample(s,uv+d).rgb+image.sample(s,uv-d).rgb
           +image.sample(s,uv+float2(d.x,-d.y)).rgb+image.sample(s,uv+float2(-d.x,d.y)).rgb)*half(side*side);
    return pixel;
}

half3 depthGaussian(texture2d<half> sharp,texture2d<half> pyramid,float2 uv,float sigma) {
    constexpr sampler s(coord::normalized,address::clamp_to_edge,filter::linear,mip_filter::linear);
    if(sigma<=1.0) return fineGaussian(sharp,uv,sigma);
    // Each level is independently Gaussian filtered after box downsampling.
    // Interpolate the variance, not logarithmic LOD: blur strength has neither
    // a focus band nor a jump when the kernel crosses a pyramid level.
    const float kernelVariance=0.92431216,boxVariance=1.0/12.0;
    float variance=kernelVariance*sigma*sigma;
    float lod=0.5*log2((variance+boxVariance)/(kernelVariance+boxVariance));
    float low=clamp(floor(lod),0.0,float(pyramid.get_num_mip_levels()-1));
    float high=min(low+1.0,float(pyramid.get_num_mip_levels()-1));
    float lowVariance=(kernelVariance+boxVariance)*exp2(2.0*low)-boxVariance;
    float highVariance=(kernelVariance+boxVariance)*exp2(2.0*high)-boxVariance;
    float blend=clamp((variance-lowVariance)/max(highVariance-lowVariance,0.00001),0.0,1.0);
    return mix(pyramid.sample(s,uv,level(low)).rgb,pyramid.sample(s,uv,level(high)).rgb,half(blend));
}

fragment half4 foldFragment(Output in [[stage_in]],texture2d<half> desktop [[texture(0)]],texture2d<half> glassImage [[texture(1)]],constant Uniforms &u [[buffer(0)]]) {
    // Keep the transparent-rest presentation contract unchanged. The window
    // compositor needs premultiplied alpha even outside the original desktop.
    half opacity=half(clamp(u.effect.z,0.0,1.0));
    if(opacity==0) return half4(0);
    float2 size=u.viewport.xy,p=in.position.xy*u.viewport.zw;
    float a=abs(u.effect.x);
    if(a>=1.57079632679) return half4(0,0,0,opacity);

    // Fixed observer at (W/2,H/2,3H), fixed content/focus plane z=0.
    // p names a PHYSICAL pixel on the lid, with a hinge along its bottom.
    // First place that pixel on the rotated lid; then intersect the observer's
    // ray through it with the stationary content plane. Only the sample moves;
    // there is no second rotation of the captured desktop or native window.
    float distance=size.y-p.y,eye=3.0*size.y;
    float depth=distance*sin(a);
    float2 physical=float2(p.x,size.y-distance*cos(a));
    float perspective=eye/max(eye-depth,1.0);
    float2 hit=size*.5+(physical-size*.5)*perspective;
    float2 uv=clamp(hit/size,0.0,1.0);

    // A thin-lens-style circle of confusion increases with distance from the
    // original focus plane, including perspective enlargement toward the eye.
    // Its strength is artistic (there is no calibrated eye tracker/lens).
    float sigmaPoints=size.y*1.5*max(u.effect.y,0.0)*depth/max(eye-depth,1.0);
    float density=float(desktop.get_width())/size.x;
    half3 pixel=depthGaussian(desktop,glassImage,uv,sigmaPoints*density);

    // The original content has finite bounds. Feather only those bounds with
    // the same optical radius; depth alone does not add an unrelated black fog.
    float2 boundary=min(hit,size-hit);
    float feather=max(0.5,sigmaPoints);
    float2 coverage=smoothstep(float2(-feather),float2(feather),boundary);
    return half4(pixel*half(coverage.x*coverage.y)*opacity,opacity);
}
fragment half4 glassGaussian(Output in [[stage_in]],texture2d<half> source [[texture(0)]],constant float4 &u [[buffer(0)]]) {
    constexpr sampler s(coord::normalized,address::clamp_to_edge,filter::linear,mip_filter::linear);
    float2 uv=in.position.xy/u.xy,d=u.w>.5?float2(0,1.0/u.y):float2(1.0/u.x,0);
    // Sigma 1, truncated at two pixels, exactly matching fineGaussian at sigma 1.
    return source.sample(s,uv,level(u.z))*0.402619947h
         +(source.sample(s,uv+d*1.182425524,level(u.z))
          +source.sample(s,uv-d*1.182425524,level(u.z)))*0.298690026h;
}
