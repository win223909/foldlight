package com.zksaga.foldlight;

import android.graphics.Bitmap;
import android.opengl.GLES30;
import android.opengl.GLSurfaceView;
import android.opengl.GLUtils;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/** One fullscreen GPU pass; Gaussian photo levels are cached only when the image changes. */
public final class FoldRenderer implements GLSurfaceView.Renderer {
    public volatile float visualTilt=Float.NaN,frostGradient=1;
    public volatile float hinge=180, blur=.08f, split=.5f, strength=1.5f;
    // Negative means the inner panel; zero explicitly disables cover rotation.
    public volatile float coverMaxAngle=-1,coverBrightness=1;
    // Standalone previews keep the original angle mapping unless the controller supplies brightness.
    public volatile float innerBrightness=Float.NaN;
    public volatile boolean innerRevealEnabled=false,referenceGlass=false;
    public volatile boolean horizontal=false, splitEnabled=true,moveRight=false;
    public volatile String error="";
    public volatile long draws=0, lastDrawNs=0;
    public volatile int surfaceWidth=0,surfaceHeight=0,displayRotation=0;
    private Bitmap bitmap;
    private boolean upload=true;
    private int texture,program,position,glassTexture;
    private GlassBlur glass;
    private final FloatBuffer vertices=ByteBuffer.allocateDirect(6*4).order(ByteOrder.nativeOrder()).asFloatBuffer();
    public FoldRenderer(Bitmap source) { bitmap=source; vertices.put(new float[]{-1,-1,3,-1,-1,3}).position(0); }
    public synchronized void setImage(Bitmap source) { bitmap=source;upload=true; }
    @Override public synchronized void onSurfaceCreated(GL10 gl,EGLConfig config) {
        try {
            program=GLES30.glCreateProgram();
            GLES30.glAttachShader(program,compile(GLES30.GL_VERTEX_SHADER,VERTEX));
            GLES30.glAttachShader(program,compile(GLES30.GL_FRAGMENT_SHADER,FRAGMENT));
            GLES30.glLinkProgram(program);int[] ok=new int[1];GLES30.glGetProgramiv(program,GLES30.GL_LINK_STATUS,ok,0);
            if(ok[0]==0)throw new IllegalStateException(GLES30.glGetProgramInfoLog(program));
            position=GLES30.glGetAttribLocation(program,"aPosition");glass=new GlassBlur();
            int[] textures=new int[1];GLES30.glGenTextures(1,textures,0);texture=textures[0];upload=true;
            GLES30.glDisable(GLES30.GL_DEPTH_TEST);GLES30.glDisable(GLES30.GL_BLEND);
            GLES30.glClearColor(.035f,.055f,.085f,1);error="";
        } catch(Exception e) { error="GPU 初始化失败："+e.getMessage(); }
    }
    @Override public void onSurfaceChanged(GL10 gl,int w,int h) { surfaceWidth=w;surfaceHeight=h;GLES30.glViewport(0,0,w,h); }
    @Override public synchronized void onDrawFrame(GL10 gl) {
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT);if(!error.isEmpty()||surfaceWidth==0||surfaceHeight==0)return;
        GLES30.glUseProgram(program);GLES30.glActiveTexture(GLES30.GL_TEXTURE0);GLES30.glBindTexture(GLES30.GL_TEXTURE_2D,texture);
        if(upload) {
            GLUtils.texImage2D(GLES30.GL_TEXTURE_2D,0,bitmap,0);
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_MIN_FILTER,GLES30.GL_LINEAR_MIPMAP_LINEAR);
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_MAG_FILTER,GLES30.GL_LINEAR);
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_WRAP_S,GLES30.GL_CLAMP_TO_EDGE);
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_WRAP_T,GLES30.GL_CLAMP_TO_EDGE);
            GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D);
            try{glassTexture=glass.build(texture,bitmap.getWidth(),bitmap.getHeight(),vertices);}catch(RuntimeException e){error="玻璃模糊初始化失败："+e.getMessage();return;}
            upload=false;
        }
        GLES30.glUseProgram(program);GLES30.glActiveTexture(GLES30.GL_TEXTURE0);GLES30.glBindTexture(GLES30.GL_TEXTURE_2D,texture);
        GLES30.glActiveTexture(GLES30.GL_TEXTURE1);GLES30.glBindTexture(GLES30.GL_TEXTURE_2D,glassTexture);GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
        int rotation=displayRotation;
        int naturalWidth=(rotation%2==0)?surfaceWidth:surfaceHeight;
        int naturalHeight=(rotation%2==0)?surfaceHeight:surfaceWidth;
        float imageAspect=(float)bitmap.getWidth()/bitmap.getHeight(),aspect=(float)naturalWidth/naturalHeight;
        GLES30.glUniform1i(uniform("photo"),0);GLES30.glUniform1i(uniform("glassPhoto"),1);
        GLES30.glUniform2f(uniform("size"),naturalWidth,naturalHeight);
        GLES30.glUniform2f(uniform("viewport"),surfaceWidth,surfaceHeight);
        GLES30.glUniform1i(uniform("rotation"),rotation);
        GLES30.glUniform2f(uniform("imageSize"),bitmap.getWidth(),bitmap.getHeight());
        GLES30.glUniform2f(uniform("uvScale"),FoldMath.coverScaleX(imageAspect,aspect),FoldMath.coverScaleY(imageAspect,aspect));
        GLES30.glUniform1f(uniform("tilt"),splitEnabled?(float)Math.toRadians((Float.isFinite(visualTilt)?visualTilt:(coverMaxAngle>=0?FoldMath.coverTilt(180-hinge,coverMaxAngle):FoldMath.panelTilt(hinge,strength)))):0);
        GLES30.glUniform1f(uniform("frostGradient"),FoldMath.clamp(frostGradient,0,1));
        GLES30.glUniform1f(uniform("frost"),FoldMath.clamp(blur,0,1));GLES30.glUniform1f(uniform("glassSigma"),glass.baseSigma);
        GLES30.glUniform1f(uniform("brightness"),FoldMath.clamp(coverBrightness,0,1));
        GLES30.glUniform1i(uniform("isCover"),coverMaxAngle>=0?1:0);
        GLES30.glUniform1i(uniform("referenceGlass"),referenceGlass?1:0);
        GLES30.glUniform1f(uniform("opening"),FoldMath.clamp((coverMaxAngle>=0?180-hinge:hinge)/180,0,1));
        GLES30.glUniform1i(uniform("innerReveal"),innerRevealEnabled?1:0);
        GLES30.glUniform1f(uniform("innerProgress"),FoldMath.clamp(Float.isNaN(innerBrightness)?(hinge-90)/90:innerBrightness,0,1));
        GLES30.glUniform1f(uniform("crease"),split);
        GLES30.glUniform1i(uniform("horizontal"),horizontal?1:0);
        GLES30.glUniform1i(uniform("moveRight"),moveRight?1:0);
        GLES30.glEnableVertexAttribArray(position);GLES30.glVertexAttribPointer(position,2,GLES30.GL_FLOAT,false,0,vertices);
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES,0,3);
        int glError=GLES30.glGetError();if(glError!=GLES30.GL_NO_ERROR)error="GPU 错误 "+glError;
        draws++;lastDrawNs=System.nanoTime();
    }
    private int uniform(String name) { return GLES30.glGetUniformLocation(program,name); }
    private static int compile(int type,String source) {
        int shader=GLES30.glCreateShader(type);GLES30.glShaderSource(shader,source);GLES30.glCompileShader(shader);
        int[] ok=new int[1];GLES30.glGetShaderiv(shader,GLES30.GL_COMPILE_STATUS,ok,0);
        if(ok[0]==0)throw new IllegalStateException(GLES30.glGetShaderInfoLog(shader));return shader;
    }
    private static final String VERTEX="#version 300 es\nin vec2 aPosition;void main(){gl_Position=vec4(aPosition,0.,1.);}";
    private static final String FRAGMENT="""
        #version 300 es
        precision highp float;
        uniform sampler2D photo,glassPhoto;
        uniform vec2 size,viewport,imageSize,uvScale;
        uniform int rotation;
        uniform float tilt,frost,frostGradient,crease,brightness,innerProgress,glassSigma;
        uniform bool horizontal,moveRight,isCover,innerReveal,referenceGlass;
        uniform float opening;
        out vec4 color;
        float glassFog(float progress,float gradient){
            return clamp((1.-progress)*(1.+.75*gradient*progress),0.,1.);
        }
        // The physical display is the moving glass. Project through it onto a separate
        // content plane; do not rotate a second solid picture inside the folding device.
        // Independently implemented study of the reference's depth/focus relationship.
        void glassScene(vec2 p,float pivot,bool moving){
            float axis=horizontal?p.y:p.x;
            float extent=horizontal?size.y:size.x;
            float crossExtent=horizontal?size.x:size.y;
            float distance=abs(axis-pivot);
            float length=max(1.,moveRight?extent-pivot:pivot);
            float x=clamp(distance/length,0.,1.);
            // A soft angular shoulder avoids reversing or disappearing beyond 90 degrees.
            float angle=moving?atan(max(tilt,0.)*.75):0.;
            float depth=distance*sin(angle),eye=max(size.x,size.y)*2.5;
            vec2 glassPoint=p;
            float projectedAxis=pivot+(axis<pivot?-1.:1.)*distance*cos(angle);
            if(horizontal)glassPoint.y=projectedAxis;else glassPoint.x=projectedAxis;
            vec2 hit=size*.5+(glassPoint-size*.5)*(eye/(eye-depth));
            vec2 texUV=(hit/size-.5)*uvScale+.5;
            float base=max(1.,max(imageSize.x*uvScale.x/size.x,imageSize.y*uvScale.y/size.y));
            vec3 sharp=textureLod(photo,texUV,log2(base)).rgb;
            // The fixed inner half bypasses focus, dimming and edge shading entirely.
            if(!moving){color=vec4(sharp,1.);return;}
            float front=1.18-1.3*smoothstep(0.,.6,opening);
            float coverFocus=smoothstep(front-.42,front+.42,x)*smoothstep(0.,.12,opening);
            float innerFocus=pow(1.-opening,.8)*smoothstep(0.,1.,x);
            float amount=isCover?coverFocus:innerFocus;
            float uniformFocus=isCover?smoothstep(0.,.6,opening):pow(1.-opening,.8);
            amount=mix(uniformFocus,amount,frostGradient);
            float radius=frost*length*.22*amount;
            float texelRadius=radius*base;
            // Ease fine detail out before the cached Gaussian takes over. A hard
            // sharp-to-Gaussian clamp at one sigma created a visible focus contour.
            float radiusSquared=texelRadius*texelRadius;
            float fineLod=log2(max(base,sqrt(base*base+radiusSquared*.25)));
            vec3 fine=textureLod(photo,texUV,fineLod).rgb;
            float softLod=log2(max(base/glassSigma,sqrt(1.+radiusSquared/(glassSigma*glassSigma))));
            vec3 soft=textureLod(glassPhoto,texUV,softLod).rgb;
            float blend=1.-exp(-radiusSquared/(glassSigma*glassSigma));
            vec3 pixel=mix(fine,soft,blend);
            // Shade only where projection leaves the content, with a soft optical edge.
            float cross=horizontal?hit.x:hit.y;
            float hitAxis=horizontal?hit.y:hit.x;
            float boundary=min(min(cross,crossExtent-cross),min(hitAxis,extent-hitAxis));
            float feather=max(.75,radius*.65);
            float coverage=smoothstep(-feather,feather,boundary);
            float level=isCover?brightness:(innerReveal?innerProgress:1.);
            // Keep the requested direction-specific envelopes, but retain the image's
            // colors through defocus instead of multiplying the whole pane to black.
            float transmission=mix(.45,1.,smoothstep(0.,1.,level));
            float shade=1.-.12*sin(angle)*x;
            color=vec4(pixel*coverage*transmission*shade,1.);
        }
        void scene(){
            vec2 p=vec2(gl_FragCoord.x,viewport.y-gl_FragCoord.y);
            // Keep the image and hinge on the physical panel even if the OS rotates this display.
            if(rotation==1)p=vec2(size.x-p.y,p.x);
            else if(rotation==2)p=size-p;
            else if(rotation==3)p=vec2(p.y,size.y-p.x);
            float axis=horizontal?p.y:p.x;
            float extent=horizontal?size.y:size.x;
            float pivot=extent*crease;
            bool moving=moveRight?axis>=pivot:axis<pivot;
            if(referenceGlass){glassScene(p,pivot,moving);return;}
            float panelAngle=moving?tilt:0.;
            vec3 backdrop=(isCover||innerReveal)?vec3(0.):vec3(.018,.022,.029);
            // With the cover pivot at its left edge, the entire plane lies outside the
            // viewport after 90 degrees. Do not inverse-project a back-facing plane.
            if(moving&&panelAngle>=1.57079632679){color=vec4(backdrop,1.);return;}
            float eye=max(size.x,size.y)*2.5;
            float distance=abs(axis-pivot);
            // Inverse projection of the actual hinged plane, rather than warping a full-screen image.
            float denominator=eye*cos(panelAngle)-distance*sin(panelAngle);
            float sourceDistance=distance*eye/max(denominator,.001);
            float z=sourceDistance*sin(panelAngle);
            float scale=eye/(eye+z);
            vec2 hit=p;
            float sourceAxis=pivot+(axis<pivot?-sourceDistance:sourceDistance);
            if(horizontal){hit.y=sourceAxis;hit.x=size.x*.5+(p.x-size.x*.5)/scale;}
            else{hit.x=sourceAxis;hit.y=size.y*.5+(p.y-size.y*.5)/scale;}
            float panelLength=moveRight?extent-pivot:pivot;
            if(!moving)panelLength=moveRight?pivot:extent-pivot;
            float projectedWidth=panelLength*cos(panelAngle)/(1.+panelLength*sin(panelAngle)/eye);
            float crossExtent=horizontal?size.x:size.y;
            float crossOffset=(horizontal?p.x:p.y)-crossExtent*.5;
            float slope=crossExtent*.5*tan(panelAngle)/eye;
            // Pixel coverage from the projected edges, independent of the GPU's 2x2 derivative quads.
            float boundary=min(projectedWidth-distance,
                (crossExtent*.5*scale-abs(crossOffset))/sqrt(1.+slope*slope));
            // Source-space distance gives both panels the same clear-to-frosted direction:
            // cover left -> right; inner hinge -> physical left edge. No uniform blur floor.
            float gradient=mix(1.,pow(clamp(sourceDistance/max(panelLength,1.),0.,1.),1.6),frostGradient);
            float visibility=1.,fog=0.;
            if(isCover){
                // A broad glass haze deepens into black; no moving cutout or irregular edge.
                fog=glassFog(brightness,gradient);
                visibility=1.-smoothstep(.25,1.,fog);
            }else if(innerReveal&&moving){
                // The whole left pane clears through glass haze, strongest at its far edge.
                // Share the cover's continuous fog curve instead of a sweeping reveal mask.
                fog=glassFog(innerProgress,gradient);
                visibility=1.-smoothstep(.25,1.,fog);
            }
            float radius=frost*panelLength*(.65*sin(panelAngle)*gradient+.45*fog*fog);
            // Feather the actual silhouette with the same directional field as the texture.
            // The stationary half never enters this blur, including at the center seam.
            float feather=max(.5,radius*1.5*clamp(cos(panelAngle)/.15,0.,1.));
            float coverage=denominator<=0.?0.:radius<.01?clamp(.5+boundary,0.,1.):smoothstep(-feather,feather,boundary);
            vec2 uv=(hit/size-.5)*uvScale+.5;
            float texelRadius=radius*max(imageSize.x/size.x,imageSize.y/size.y);
            float axisDerivative=eye*eye*cos(panelAngle)/max(denominator*denominator,.0001);
            float crossDerivative=crossOffset*tan(panelAngle)/eye/max(scale*scale,.0001);
            vec2 textureScale=uvScale*imageSize/size;
            vec2 along=horizontal?vec2(crossDerivative,axisDerivative):vec2(axisDerivative,crossDerivative);
            vec2 across=horizontal?vec2(1./scale,0.):vec2(0.,1./scale);
            float footprint=max(length(along*textureScale),length(across*textureScale));
            float base=max(footprint,1.);
            float lod=log2(base);
            float glassLod=log2(max(max(base,texelRadius)/glassSigma,1.));
            if(coverage<=0.){color=vec4(backdrop,1.);return;}
            vec3 sharp=textureLod(photo,uv,lod).rgb;
            vec3 soft=textureLod(glassPhoto,uv,glassLod).rgb;
            vec3 pixel=mix(sharp,soft,clamp(texelRadius*texelRadius/(glassSigma*glassSigma),0.,1.));
            float veil=clamp(frost*gradient*sin(panelAngle)*.22,0.,.045);
            pixel=mix(pixel,vec3(.94,.97,1.),veil);
            float edge=1.-smoothstep(0.,max(1.,extent*.012),distance);
            float highlight=edge*sin(panelAngle)*.07;
            color=vec4(mix(backdrop,(pixel+highlight)*visibility,coverage),1.);
        }
        void main(){scene();}
        """;
}
