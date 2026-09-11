// Separable Gaussian mip levels, cached until the source image or its size changes.
// Sigma stays four texels wide at every level, avoiding blocky mip-only blur.
export class GlassBlur {
 constructor(gl) {
  this.gl=gl;this.framebuffer=gl.createFramebuffer();this.scratch=gl.createTexture();
  const weights=Array.from({length:13},(_,i)=>Math.exp(-i*i/32));
  const total=weights[0]+2*weights.slice(1).reduce((a,b)=>a+b,0);
  let body=`vec4 c=textureLod(source,uv,level)*${(weights[0]/total).toFixed(9)};`;
  for(let i=1;i<13;i+=2){const w=weights[i]+weights[i+1],d=(i*weights[i]+(i+1)*weights[i+1])/w;body+=`c+=(textureLod(source,uv+direction*${d.toFixed(9)},level)+textureLod(source,uv-direction*${d.toFixed(9)},level))*${(w/total).toFixed(9)};`}
  this.program=gl.createProgram();
  for(const [type,code] of [[gl.VERTEX_SHADER,`#version 300 es\nvoid main(){vec2 p=vec2(float((gl_VertexID<<1)&2),float(gl_VertexID&2));gl_Position=vec4(p*2.-1.,0,1);}`],[gl.FRAGMENT_SHADER,`#version 300 es\nprecision highp float;uniform sampler2D source;uniform vec2 size,direction;uniform float level;out vec4 color;void main(){vec2 uv=gl_FragCoord.xy/size;${body}color=c;}`]]){
   const shader=gl.createShader(type);gl.shaderSource(shader,code);gl.compileShader(shader);if(!gl.getShaderParameter(shader,gl.COMPILE_STATUS))throw Error(gl.getShaderInfoLog(shader));gl.attachShader(this.program,shader);gl.deleteShader(shader);
  }
  gl.linkProgram(this.program);if(!gl.getProgramParameter(this.program,gl.LINK_STATUS))throw Error(gl.getProgramInfoLog(this.program));
  this.uniforms=Object.fromEntries(['source','size','direction','level'].map(k=>[k,gl.getUniformLocation(this.program,k)]));
 }
 parameters(mip){const g=this.gl;g.texParameteri(g.TEXTURE_2D,g.TEXTURE_MIN_FILTER,mip?g.LINEAR_MIPMAP_LINEAR:g.LINEAR);g.texParameteri(g.TEXTURE_2D,g.TEXTURE_MAG_FILTER,g.LINEAR);g.texParameteri(g.TEXTURE_2D,g.TEXTURE_WRAP_S,g.CLAMP_TO_EDGE);g.texParameteri(g.TEXTURE_2D,g.TEXTURE_WRAP_T,g.CLAMP_TO_EDGE)}
 build(original,width,height){
  const g=this.gl,u=this.uniforms,framebuffer=g.getParameter(g.FRAMEBUFFER_BINDING),viewport=g.getParameter(g.VIEWPORT),program=g.getParameter(g.CURRENT_PROGRAM);let sourceLevel=0;
  while(Math.max(width,height)>1024){width=Math.max(1,width>>1);height=Math.max(1,height>>1);sourceLevel++}this.baseSigma=4*2**sourceLevel;
  try{
   if(this.texture)g.deleteTexture(this.texture);this.texture=g.createTexture();g.activeTexture(g.TEXTURE0);g.bindTexture(g.TEXTURE_2D,this.texture);
   const levels=1+Math.floor(Math.log2(Math.max(width,height)));g.texStorage2D(g.TEXTURE_2D,levels,g.RGBA8,width,height);this.parameters(true);
   g.bindFramebuffer(g.FRAMEBUFFER,this.framebuffer);g.useProgram(this.program);g.uniform1i(u.source,0);
   for(let level=0;level<levels;level++){
    const w=Math.max(1,width>>level),h=Math.max(1,height>>level);g.viewport(0,0,w,h);g.uniform2f(u.size,w,h);
    g.bindTexture(g.TEXTURE_2D,this.scratch);g.texImage2D(g.TEXTURE_2D,0,g.RGBA8,w,h,0,g.RGBA,g.UNSIGNED_BYTE,null);this.parameters(false);
    g.framebufferTexture2D(g.FRAMEBUFFER,g.COLOR_ATTACHMENT0,g.TEXTURE_2D,this.scratch,0);
    if(g.checkFramebufferStatus(g.FRAMEBUFFER)!==g.FRAMEBUFFER_COMPLETE)throw Error('Glass framebuffer incomplete');
    g.bindTexture(g.TEXTURE_2D,original);g.uniform1f(u.level,level+sourceLevel);g.uniform2f(u.direction,1/w,0);g.drawArrays(g.TRIANGLES,0,3);
    g.framebufferTexture2D(g.FRAMEBUFFER,g.COLOR_ATTACHMENT0,g.TEXTURE_2D,this.texture,level);
    if(g.checkFramebufferStatus(g.FRAMEBUFFER)!==g.FRAMEBUFFER_COMPLETE)throw Error('Glass level incomplete');
    g.bindTexture(g.TEXTURE_2D,this.scratch);g.uniform1f(u.level,0);g.uniform2f(u.direction,0,1/h);g.drawArrays(g.TRIANGLES,0,3);
   }
  }finally{g.bindFramebuffer(g.FRAMEBUFFER,framebuffer);g.viewport(...viewport);g.useProgram(program)}
  return this.texture;
 }
}
