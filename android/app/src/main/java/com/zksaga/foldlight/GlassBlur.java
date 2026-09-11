package com.zksaga.foldlight;

import android.opengl.GLES30;
import java.nio.FloatBuffer;
import java.util.Locale;

/** Cached separable Gaussian levels. Only regenerated when a photo is uploaded. */
final class GlassBlur {
    private final int program,framebuffer,scratch,position;
    private int texture;
    float baseSigma=4;
    GlassBlur(){
        program=GLES30.glCreateProgram();
        GLES30.glAttachShader(program,compile(GLES30.GL_VERTEX_SHADER,"#version 300 es\nin vec2 p;void main(){gl_Position=vec4(p,0.,1.);}"));
        GLES30.glAttachShader(program,compile(GLES30.GL_FRAGMENT_SHADER,kernel()));
        GLES30.glLinkProgram(program);int[] ok=new int[1];GLES30.glGetProgramiv(program,GLES30.GL_LINK_STATUS,ok,0);
        if(ok[0]==0)throw new IllegalStateException(GLES30.glGetProgramInfoLog(program));
        position=GLES30.glGetAttribLocation(program,"p");
        int[] id=new int[1];GLES30.glGenFramebuffers(1,id,0);framebuffer=id[0];GLES30.glGenTextures(1,id,0);scratch=id[0];
    }
    int build(int original,int width,int height,FloatBuffer vertices){
        int sourceLevel=0;while(Math.max(width,height)>1024){width=Math.max(1,width/2);height=Math.max(1,height/2);sourceLevel++;}
        baseSigma=4*(1<<sourceLevel);
        int[] previous=new int[1],viewport=new int[4];GLES30.glGetIntegerv(GLES30.GL_FRAMEBUFFER_BINDING,previous,0);GLES30.glGetIntegerv(GLES30.GL_VIEWPORT,viewport,0);
        try{
            if(texture!=0)GLES30.glDeleteTextures(1,new int[]{texture},0);
            int[] id=new int[1];GLES30.glGenTextures(1,id,0);texture=id[0];
            int levels=1+(int)Math.floor(Math.log(Math.max(width,height))/Math.log(2));
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0);GLES30.glBindTexture(GLES30.GL_TEXTURE_2D,texture);
            GLES30.glTexStorage2D(GLES30.GL_TEXTURE_2D,levels,GLES30.GL_RGBA8,width,height);parameters(true);
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER,framebuffer);GLES30.glUseProgram(program);
            GLES30.glUniform1i(GLES30.glGetUniformLocation(program,"source"),0);
            GLES30.glEnableVertexAttribArray(position);vertices.position(0);GLES30.glVertexAttribPointer(position,2,GLES30.GL_FLOAT,false,0,vertices);
            for(int level=0;level<levels;level++){
                int w=Math.max(1,width>>level),h=Math.max(1,height>>level);
                GLES30.glViewport(0,0,w,h);GLES30.glUniform2f(GLES30.glGetUniformLocation(program,"size"),w,h);
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D,scratch);GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D,0,GLES30.GL_RGBA8,w,h,0,GLES30.GL_RGBA,GLES30.GL_UNSIGNED_BYTE,null);parameters(false);
                GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER,GLES30.GL_COLOR_ATTACHMENT0,GLES30.GL_TEXTURE_2D,scratch,0);checkTarget();
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D,original);GLES30.glUniform1f(GLES30.glGetUniformLocation(program,"level"),level+sourceLevel);
                GLES30.glUniform2f(GLES30.glGetUniformLocation(program,"direction"),1f/w,0);GLES30.glDrawArrays(GLES30.GL_TRIANGLES,0,3);
                GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER,GLES30.GL_COLOR_ATTACHMENT0,GLES30.GL_TEXTURE_2D,texture,level);checkTarget();
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D,scratch);GLES30.glUniform1f(GLES30.glGetUniformLocation(program,"level"),0);
                GLES30.glUniform2f(GLES30.glGetUniformLocation(program,"direction"),0,1f/h);GLES30.glDrawArrays(GLES30.GL_TRIANGLES,0,3);
            }
            return texture;
        }finally{GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER,previous[0]);GLES30.glViewport(viewport[0],viewport[1],viewport[2],viewport[3]);}
    }
    private static void parameters(boolean mip){
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_MIN_FILTER,mip?GLES30.GL_LINEAR_MIPMAP_LINEAR:GLES30.GL_LINEAR);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_MAG_FILTER,GLES30.GL_LINEAR);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_WRAP_S,GLES30.GL_CLAMP_TO_EDGE);GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_WRAP_T,GLES30.GL_CLAMP_TO_EDGE);
    }
    private static void checkTarget(){if(GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER)!=GLES30.GL_FRAMEBUFFER_COMPLETE)throw new IllegalStateException("Glass blur framebuffer incomplete");}
    private static int compile(int type,String source){
        int shader=GLES30.glCreateShader(type);GLES30.glShaderSource(shader,source);GLES30.glCompileShader(shader);int[] ok=new int[1];GLES30.glGetShaderiv(shader,GLES30.GL_COMPILE_STATUS,ok,0);
        if(ok[0]==0)throw new IllegalStateException(GLES30.glGetShaderInfoLog(shader));return shader;
    }
    private static String kernel(){
        // Sigma 4 at each level keeps the final sampling grid much finer than the blur.
        double[] weights=new double[13];double total=0;for(int i=0;i<13;i++){weights[i]=Math.exp(-i*i/32.0);total+=weights[i]*(i==0?1:2);}
        StringBuilder s=new StringBuilder("#version 300 es\nprecision highp float;uniform sampler2D source;uniform vec2 size,direction;uniform float level;out vec4 color;void main(){vec2 uv=gl_FragCoord.xy/size;\n");
        s.append(String.format(Locale.US,"vec4 c=textureLod(source,uv,level)*%.9f;\n",weights[0]/total));
        for(int i=1;i<13;i+=2){double w=weights[i]+weights[i+1],offset=(i*weights[i]+(i+1)*weights[i+1])/w;
            s.append(String.format(Locale.US,"c+=(textureLod(source,uv+direction*%.9f,level)+textureLod(source,uv-direction*%.9f,level))*%.9f;\n",offset,offset,w/total));}
        return s.append("color=c;}").toString();
    }
}
