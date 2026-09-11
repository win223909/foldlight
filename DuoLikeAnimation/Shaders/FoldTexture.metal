#include <metal_stdlib>
using namespace metal;

struct FoldTextureUniforms {
    float4 viewport; // width/height in points; framebuffer-pixel to point scale
    float4 fold;     // sine, cosine, hinge x in points, eye distance in points
    float4 material; // blur spread, darkening, source texels per point, Gaussian base sigma
    float4 mapping;  // hit-point to source-UV scale and centered aspect-fill offset
};

struct FoldTextureVertexOut { float4 position [[position]]; };

vertex FoldTextureVertexOut foldTextureVertex(uint vertexID [[vertex_id]]) {
    const float2 positions[] = { float2(-1, -1), float2(3, -1), float2(-1, 3) };
    return { float4(positions[vertexID], 0, 1) };
}

fragment half4 foldTextureFragment(FoldTextureVertexOut in [[stage_in]],
                                   texture2d<half> photo [[texture(0)]],
                                   texture2d<half> glassPhoto [[texture(1)]],
                                   constant FoldTextureUniforms &u [[buffer(0)]]) {
    if(u.fold.y<=0.00001) return half4(0,0,0,1);
    const float2 size = u.viewport.xy;
    const float2 p = in.position.xy * u.viewport.zw;
    const float hingeX = u.fold.z;
    const float side = hingeX > 0 ? -1.0 : 1.0;
    const float distance = abs(p.x - hingeX);
    const float3 glass = float3(hingeX + side * distance * u.fold.y, p.y, distance * u.fold.x);
    const float3 eye = float3(size * 0.5, u.fold.w);
    const float depth = eye.z - glass.z;
    if (depth <= 1e-3) { return half4(0, 0, 0, 1); }
    const float2 hit = eye.xy + (glass.xy - eye.xy) * (eye.z / depth);
    const float2 uv = hit * u.mapping.xy + u.mapping.zw;

    const float gradient=pow(clamp(distance/size.x,0.0,1.0),1.6);
    const float radius=size.x*.65*u.fold.x*gradient*(u.material.x/.65);
    const float radiusInTexels=radius*u.material.z;
    const float2 texelPosition=uv*float2(photo.get_width(),photo.get_height());
    const float footprint=max(1.0,max(length(dfdx(texelPosition)),length(dfdy(texelPosition))));
    const float sigma=max(1.0,u.material.w);
    constexpr sampler filtering(coord::normalized,address::clamp_to_edge,min_filter::linear,mag_filter::linear,mip_filter::linear);
    half3 sharp=photo.sample(filtering,uv,level(log2(footprint))).rgb;
    half3 soft=glassPhoto.sample(filtering,uv,level(log2(max(1.0,max(footprint,radiusInTexels)/sigma)))).rgb;
    half3 pixel=mix(sharp,soft,half(clamp(radiusInTexels*radiusInTexels/(sigma*sigma),0.0,1.0)));
    float fog=clamp(radius/(size.x*.13),0.0,1.0),visibility=1.0-smoothstep(.25,1.0,fog);
    float boundary=min(min(hit.x,size.x-hit.x),min(hit.y,size.y-hit.y)),feather=max(.5,radius);
    float coverage=radius<.01?step(0.0,boundary):smoothstep(-feather,feather,boundary);
    return half4(pixel*half(visibility*coverage),1.0h);
}
fragment half4 foldGlassDownsample(FoldTextureVertexOut in [[stage_in]],texture2d<half> source [[texture(0)]],constant float4 &u [[buffer(0)]]) {
    constexpr sampler s(coord::normalized,address::clamp_to_edge,filter::linear);
    float2 uv=in.position.xy/u.xy;half4 c=half4(0);
    for(int y=0;y<4;y++)for(int x=0;x<4;x++)c+=source.sample(s,uv+(float2(x,y)/4.0-.375)/u.xy);
    return c/16.0h;
}
fragment half4 foldGlassGaussian(FoldTextureVertexOut in [[stage_in]],texture2d<half> source [[texture(0)]],constant float4 &u [[buffer(0)]]) {
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
