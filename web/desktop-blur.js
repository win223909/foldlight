// Desktop-only Gaussian cache. Phone and Fold keep their existing blur pipeline.
// The original texture must have a complete mip chain (app.js generates it on
// upload). Build only when that texture's pixels or dimensions change.
export class DesktopBlur {
 constructor(gl) {
  this.gl=gl;this.baseSigma=1;this.width=0;this.height=0;
  this.texture=null;this.scratch=null;this.original=null;
  this.framebuffer=gl.createFramebuffer();this.program=gl.createProgram();
  const vertex=`#version 300 es
void main(){vec2 p=vec2(float((gl_VertexID<<1)&2),float(gl_VertexID&2));gl_Position=vec4(p*2.-1.,0,1);}`;
  const fragment=`#version 300 es
precision highp float;
uniform sampler2D source;uniform vec2 size,direction;uniform float level;
out vec4 color;
void main(){
 vec2 uv=gl_FragCoord.xy/size;
 // Sigma 1, truncated at two pixels. Bilinear paired taps reproduce the
 // five-tap Gaussian in three fetches, matching desktopFineGaussian at 1.
 color=textureLod(source,uv,level)*.402619947
  +(textureLod(source,uv+direction*1.182425524,level)
   +textureLod(source,uv-direction*1.182425524,level))*.298690026;
}`;
  for(const [type,code] of [[gl.VERTEX_SHADER,vertex],[gl.FRAGMENT_SHADER,fragment]]){
   const shader=gl.createShader(type);gl.shaderSource(shader,code);gl.compileShader(shader);
   if(!gl.getShaderParameter(shader,gl.COMPILE_STATUS)){const message=gl.getShaderInfoLog(shader);gl.deleteShader(shader);throw new Error(message);}
   gl.attachShader(this.program,shader);gl.deleteShader(shader);
  }
  gl.linkProgram(this.program);if(!gl.getProgramParameter(this.program,gl.LINK_STATUS))throw new Error(gl.getProgramInfoLog(this.program));
  this.uniforms=Object.fromEntries(['source','size','direction','level'].map(k=>[k,gl.getUniformLocation(this.program,k)]));
 }
 allocate(width,height,levels){
  const g=this.gl,t=g.createTexture();g.bindTexture(g.TEXTURE_2D,t);
  g.texStorage2D(g.TEXTURE_2D,levels,g.RGBA8,width,height);
  g.texParameteri(g.TEXTURE_2D,g.TEXTURE_MIN_FILTER,g.LINEAR_MIPMAP_LINEAR);
  g.texParameteri(g.TEXTURE_2D,g.TEXTURE_MAG_FILTER,g.LINEAR);
  g.texParameteri(g.TEXTURE_2D,g.TEXTURE_WRAP_S,g.CLAMP_TO_EDGE);
  g.texParameteri(g.TEXTURE_2D,g.TEXTURE_WRAP_T,g.CLAMP_TO_EDGE);
  return t;
 }
 build(original,width,height,{refresh=true}={}){
  if(!Number.isInteger(width)||!Number.isInteger(height)||width<1||height<1)throw new RangeError('Invalid desktop blur size');
  const resized=width!==this.width||height!==this.height;
  if(!refresh&&!resized&&original===this.original&&this.texture)return this.texture;
  const g=this.gl,u=this.uniforms;
  const framebuffer=g.getParameter(g.FRAMEBUFFER_BINDING),viewport=g.getParameter(g.VIEWPORT),program=g.getParameter(g.CURRENT_PROGRAM),active=g.getParameter(g.ACTIVE_TEXTURE);
  g.activeTexture(g.TEXTURE0);let binding=g.getParameter(g.TEXTURE_BINDING_2D);
  try{
   const levels=1+Math.floor(Math.log2(Math.max(width,height)));
   if(resized||!this.texture){
    if(binding===this.texture||binding===this.scratch)binding=null;
    if(this.texture)g.deleteTexture(this.texture);if(this.scratch)g.deleteTexture(this.scratch);
    this.texture=this.allocate(width,height,levels);this.scratch=this.allocate(width,height,levels);
    this.width=width;this.height=height;
   }
   g.bindFramebuffer(g.FRAMEBUFFER,this.framebuffer);g.useProgram(this.program);g.uniform1i(u.source,0);
   for(let level=0;level<levels;level++){
    const w=Math.max(1,width>>level),h=Math.max(1,height>>level);
    g.viewport(0,0,w,h);g.uniform2f(u.size,w,h);g.uniform1f(u.level,level);
    for(const [input,output,dx,dy] of [[original,this.scratch,1/w,0],[this.scratch,this.texture,0,1/h]]){
     g.framebufferTexture2D(g.FRAMEBUFFER,g.COLOR_ATTACHMENT0,g.TEXTURE_2D,output,level);
     if(g.checkFramebufferStatus(g.FRAMEBUFFER)!==g.FRAMEBUFFER_COMPLETE)throw new Error('Desktop Gaussian framebuffer incomplete');
     g.bindTexture(g.TEXTURE_2D,input);g.uniform2f(u.direction,dx,dy);g.drawArrays(g.TRIANGLES,0,3);
    }
   }
   this.original=original;
  }finally{
   g.bindFramebuffer(g.FRAMEBUFFER,framebuffer);g.viewport(...viewport);g.useProgram(program);
   g.bindTexture(g.TEXTURE_2D,binding);g.activeTexture(active);
  }
  return this.texture;
 }
 dispose(){
  const g=this.gl;if(this.texture)g.deleteTexture(this.texture);if(this.scratch)g.deleteTexture(this.scratch);
  g.deleteFramebuffer(this.framebuffer);g.deleteProgram(this.program);this.texture=this.scratch=null;this.original=null;
 }
}
