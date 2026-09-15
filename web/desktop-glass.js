// Website-only optics: phone/Fold shaders remain independently versioned.
// The product model moves the physical screen; this shader samples a stationary
// image plane from the same observer instead of folding the picture a second time.
export function previewFragment(phoneFragment){
 const entry='void main(){scene();}';
 if(!phoneFragment.endsWith(entry))throw new Error('Unexpected phone shader entry');
 return phoneFragment.slice(0,-entry.length)+`
uniform mat3 desktopProjection;
uniform vec2 desktopDepth;
// Below one source pixel, evaluate the same separable five-tap Gaussian
// directly. Nine bilinear fetches replace a clear/large-blur crossfade.
vec3 desktopFineGaussian(vec2 uv,float sigma){
    if(sigma<.08)return textureLod(photo,uv,0.).rgb;
    float variance=sigma*sigma;
    float w1=exp(-.5/variance),w2=exp(-2./variance),pair=w1+w2;
    float offset=(w1+2.*w2)/max(pair,1e-8),center=1./(1.+2.*pair),side=pair*center;
    vec2 d=offset/imageSize;
    vec3 pixel=textureLod(photo,uv,0.).rgb*(center*center);
    pixel+=(textureLod(photo,uv+vec2(d.x,0.),0.).rgb+textureLod(photo,uv-vec2(d.x,0.),0.).rgb
           +textureLod(photo,uv+vec2(0.,d.y),0.).rgb+textureLod(photo,uv-vec2(0.,d.y),0.).rgb)*(center*side);
    pixel+=(textureLod(photo,uv+d,0.).rgb+textureLod(photo,uv-d,0.).rgb
           +textureLod(photo,uv+vec2(d.x,-d.y),0.).rgb+textureLod(photo,uv+vec2(-d.x,d.y),0.).rgb)*(side*side);
    return pixel;
}
vec3 desktopDepthGaussian(vec2 uv,float sigma){
    if(sigma<=1.)return desktopFineGaussian(uv,sigma);
    // A full-resolution sigma-1 Gaussian exists at every mip level. Account
    // for the source box-downsample variance, then blend neighboring Gaussian
    // variances. No original-image mip or large sharp core enters this branch.
    const float kernelVariance=.92431216,boxVariance=1./12.;
    float variance=kernelVariance*sigma*sigma;
    float lod=.5*log2((variance+boxVariance)/(kernelVariance+boxVariance));
    float last=floor(log2(max(imageSize.x,imageSize.y)));
    float low=clamp(floor(lod),0.,last),high=min(low+1.,last);
    float lowVariance=(kernelVariance+boxVariance)*exp2(2.*low)-boxVariance;
    float highVariance=(kernelVariance+boxVariance)*exp2(2.*high)-boxVariance;
    float blend=clamp((variance-lowVariance)/max(highVariance-lowVariance,.00001),0.,1.);
    return mix(textureLod(glassPhoto,uv,low).rgb,textureLod(glassPhoto,uv,high).rgb,blend);
}
void desktopScene(){
    vec2 local=vec2(gl_FragCoord.x,viewport.y-gl_FragCoord.y)/viewport;
    vec3 projected=desktopProjection*vec3(local,1.);
    vec2 hit=projected.xy/projected.z;
    float depth=max(0.,desktopDepth.x+desktopDepth.y*local.y);
    // Distance to the original focal plane is continuous from hinge to rim.
    // No moving sharp/blur threshold, additional contraction or global fade.
    float radius=frost*imageSize.y*.18*depth;
    vec3 pixel=desktopDepthGaussian(hit,radius);
    // Only rays outside the image plane shade out, softened by the same glass.
    float boundary=min(min(hit.x,1.-hit.x)*imageSize.x,min(hit.y,1.-hit.y)*imageSize.y);
    float feather=max(.5,radius*.6);
    float coverage=smoothstep(-feather,feather,boundary);
    color=vec4(pixel*coverage,1.);
}
void main(){if(horizontal)desktopScene();else scene();}
`;
}
