#include <metal_stdlib>
using namespace metal;
struct Output { float4 position [[position]]; };
struct Uniforms { float4 viewport; float4 effect; };
vertex Output foldVertex(uint id [[vertex_id]]) {
    const float2 p[]={float2(-1,-1),float2(3,-1),float2(-1,3)};
    return {float4(p[id],0,1)};
}
fragment half4 foldFragment(Output in [[stage_in]],texture2d<half> desktop [[texture(0)]],texture2d<half> glassImage [[texture(1)]],constant Uniforms &u [[buffer(0)]]) {
    constexpr sampler s(coord::normalized,address::clamp_to_edge,filter::linear,mip_filter::linear);
    // Core Animation expects premultiplied alpha. A fully transparent rest frame
    // exposes the original desktop without hiding/showing the native window.
    half opacity=half(clamp(u.effect.z,0.0,1.0));
    if(opacity==0) return half4(0);
    float2 size=u.viewport.xy, p=in.position.xy*u.viewport.zw;
    float a=abs(u.effect.x),distance=size.y-p.y;
    // The physical MacBook hinge is the bottom edge; opening past the baseline is zero.
    if(a>=1.57079632679) return half4(0,0,0,opacity);
    float z=distance*sin(a),eye=max(1920.0,max(size.x,size.y)*3.0);
    float2 glass=float2(p.x,size.y-distance*cos(a));
    float2 hit=size*.5+(glass-size*.5)*(eye/max(1.0,eye-z));
    float gradient=pow(clamp(distance/size.y,0.0,1.0),1.6);
    float radius=size.y*.65*sin(a)*gradient*u.effect.y;
    float density=float(desktop.get_width())/size.x,texelRadius=radius*density,sigma=max(u.effect.w,1.0);
    float2 uv=clamp(hit/size,0.0,1.0);
    half3 sharp=desktop.sample(s,uv).rgb;
    half3 soft=glassImage.sample(s,uv,level(log2(max(max(1.0,density),texelRadius)/sigma))).rgb;
    half3 pixel=mix(sharp,soft,half(clamp(texelRadius*texelRadius/(sigma*sigma),0.0,1.0)));
    float fog=clamp(radius/(size.y*.13),0.0,1.0),visibility=1.0-smoothstep(.25,1.0,fog);
    float boundary=min(min(hit.x,size.x-hit.x),min(hit.y,size.y-hit.y));
    float feather=max(.5,radius),coverage=radius<.01?step(0.0,boundary):smoothstep(-feather,feather,boundary);
    return half4(pixel*half(visibility*coverage)*opacity,opacity);
}
fragment half4 glassDownsample(Output in [[stage_in]],texture2d<half> source [[texture(0)]],constant float4 &u [[buffer(0)]]) {
    constexpr sampler s(coord::normalized,address::clamp_to_edge,filter::linear);
    float2 uv=in.position.xy/u.xy;half4 c=half4(0);
    for(int y=0;y<4;y++)for(int x=0;x<4;x++)c+=source.sample(s,uv+(float2(x,y)/4.0-.375)/u.xy);
    return c/16.0h;
}
fragment half4 glassGaussian(Output in [[stage_in]],texture2d<half> source [[texture(0)]],constant float4 &u [[buffer(0)]]) {
    constexpr sampler s(coord::normalized,address::clamp_to_edge,filter::linear,mip_filter::linear);
    float2 uv=in.position.xy/u.xy,d=u.w>.5?float2(0,1.0/u.y):float2(1.0/u.x,0);
    half4 c=source.sample(s,uv,level(u.z))*0.099908358h;
    c+=(source.sample(s,uv+d*1.476579651,level(u.z))+source.sample(s,uv-d*1.476579651,level(u.z)))*0.185003318h;
    c+=(source.sample(s,uv+d*3.445529535,level(u.z))+source.sample(s,uv-d*3.445529535,level(u.z)))*0.136012268h;
    c+=(source.sample(s,uv+d*5.414898846,level(u.z))+source.sample(s,uv-d*5.414898846,level(u.z)))*0.078176874h;
    c+=(source.sample(s,uv+d*7.384912144,level(u.z))+source.sample(s,uv-d*7.384912144,level(u.z)))*0.035127824h;
    c+=(source.sample(s,uv+d*9.355774894,level(u.z))+source.sample(s,uv-d*9.355774894,level(u.z)))*0.012338327h;
    c+=(source.sample(s,uv+d*11.327668301,level(u.z))+source.sample(s,uv-d*11.327668301,level(u.z)))*0.003387211h;
    return c;
}
