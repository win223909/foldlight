// Dimension matching is only a default suggestion; the upload panel can override it.
export function isPhoneScreenshot(iw,ih,screenWidth,screenHeight){
 const ratio=ih/iw,deviceRatio=Math.max(screenWidth,screenHeight)/Math.min(screenWidth,screenHeight);
 return ratio>1.7&&ratio<2.4&&Math.abs(ratio/deviceRatio-1)<.015;
}

export function screenLayout({iw,ih,w,h,immersed=false,installed=false,hasStatus=false,safeTop=0}){
 // A portrait iPhone screenshot includes approximately 7% status-bar space.
 // Remove it only when the real iOS status bar is present. Safe-area=0 is valid:
 // some iOS releases reserve that space outside the webpage instead.
 const adapt=immersed&&installed&&hasStatus&&ih/iw>1.7;
 const sy=adapt?Math.round(ih*.07):0;
 const top=adapt?Math.min(Math.max(safeTop,0),h*.15):0;
 const sh=ih-sy;
 const scale=immersed?Math.min(w/iw,(h-top)/sh):Math.max(w/iw,h/sh);
 const width=iw*scale,height=sh*scale;
 return {sx:0,sy,sw:iw,sh,x:(w-width)/2,y:top+(h-top-height)/2,width,height};
}
