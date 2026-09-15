package com.zksaga.foldlight;
import android.graphics.*;
/** Original demo artwork. No downloaded device screenshot or baked-in status bar. */
public final class DemoImage {
 public static Bitmap create(boolean cover) {
  FoldDeviceProfile profile=FoldDeviceProfile.forModel(android.os.Build.MODEL);
  int w=profile==null?(cover?1248:2448):profile.width(cover),h=profile==null?(cover?1972:1848):profile.height(cover);
  Bitmap b=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(b);Paint p=new Paint(3);
  int[] colors=cover?new int[]{0xff092838,0xff28637e,0xff76b8c6,0xffd6e7dd}:new int[]{0xff321d34,0xff965653,0xffdfab83,0xfff5dec0};
  p.setShader(new LinearGradient(0,0,w,h,colors,null,Shader.TileMode.CLAMP));c.drawRect(0,0,w,h,p);
  p.setShader(new RadialGradient(w*.12f,h*.32f,Math.max(w,h)*.72f,new int[]{0x99e0f3eb,0x00ffffff},null,Shader.TileMode.CLAMP));c.drawRect(0,0,w,h,p);p.setShader(null);
  for(int i=0;i<7;i++){
   p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(w*.0012f);p.setColor(0x35ffffff);
   c.drawOval(new RectF(-w*.65f+i*w*.13f,h*.2f+i*h*.075f,w*.8f+i*w*.22f,h*1.4f+i*h*.1f),p);
  }
  return b;
 }
 public static Bitmap create() {
  Bitmap b=Bitmap.createBitmap(1600,1800,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(b);Paint p=new Paint(3);
  p.setShader(new LinearGradient(0,0,1500,1800,new int[]{0xff10243e,0xff376483,0xffc6a280,0xffefceaa},null,Shader.TileMode.CLAMP));c.drawRect(0,0,1600,1800,p);
  p.setShader(new RadialGradient(250,350,1350,new int[]{0xff96bdce,0x00495f83},null,Shader.TileMode.CLAMP));c.drawRect(0,0,1600,1800,p);p.setShader(null);
  p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2);p.setColor(0x55ffffff);
  for(int i=0;i<6;i++)c.drawOval(new RectF(-500+i*110,350+i*80,1700+i*110,2100+i*80),p);
  p.setStyle(Paint.Style.FILL);p.setColor(0xfff9f6ed);p.setTypeface(Typeface.create("sans-serif-light",Typeface.NORMAL));p.setTextAlign(Paint.Align.CENTER);p.setTextSize(88);c.drawText("Unfold the light.",800,470,p);
  p.setTextSize(32);p.setColor(0xdde8eef1);c.drawText("FOLDLIGHT  /  A STUDY IN LIGHT",800,355,p);
  p.setTextSize(36);c.drawText("轻轻展开，看见不同。",800,550,p);
  p.setTextAlign(Paint.Align.LEFT);p.setColor(0x22ffffff);c.drawRoundRect(new RectF(140,820,745,1160),42,42,p);c.drawRoundRect(new RectF(855,820,1460,1160),42,42,p);
  p.setColor(0xfff9f6ed);p.setTextSize(35);c.drawText("LIGHT",180,890,p);c.drawText("PERSPECTIVE",895,890,p);
  p.setTextSize(100);c.drawText("08",180,1050,p);c.drawText("180°",895,1050,p);
  return b;
 }
}
