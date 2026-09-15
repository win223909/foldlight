// Original, locally drawn desktop artwork. No screenshots or external assets.
// The scene is painted only when requested by the page; it owns no animation loop.
const FONT = '-apple-system, BlinkMacSystemFont, "Segoe UI", "PingFang SC", sans-serif';

function rounded(ctx, x, y, w, h, r, fill, stroke) {
  ctx.beginPath();
  ctx.roundRect(x, y, w, h, r);
  if (fill) { ctx.fillStyle = fill; ctx.fill(); }
  if (stroke) { ctx.strokeStyle = stroke; ctx.lineWidth = 1; ctx.stroke(); }
}
function type(ctx, value, x, y, size, color, weight = 400, align = 'left') {
  ctx.font = `${weight} ${size}px ${FONT}`;
  ctx.fillStyle = color;
  ctx.textAlign = align;
  ctx.textBaseline = 'alphabetic';
  ctx.fillText(value, x, y);
}
function gradient(ctx, x, y, endX, endY, stops) {
  const g = ctx.createLinearGradient(x, y, endX, endY);
  stops.forEach(([stop, color]) => g.addColorStop(stop, color));
  return g;
}
function stroke(ctx, points, color, width = 2) {
  ctx.beginPath();
  points.forEach(([x,y], index) => index ? ctx.lineTo(x,y) : ctx.moveTo(x,y));
  ctx.strokeStyle = color; ctx.lineWidth = width; ctx.lineCap = 'round'; ctx.lineJoin = 'round'; ctx.stroke();
}
function circle(ctx, x, y, radius, fill) {
  ctx.beginPath(); ctx.arc(x,y,radius,0,Math.PI*2); ctx.fillStyle=fill; ctx.fill();
}

function landscape(ctx, x, y, w, h, theme = 'coast') {
  const themes = {
    coast: ['#e0ebec','#b2d1d1','#60979f','#376f7c','#194a5c','#fff0d9'],
    dune: ['#f3e5d8','#e7c7b5','#c48e78','#a5675c','#784d50','#fff6db'],
    night: ['#b1becf','#8cabbf','#658899','#416578','#294456','#fae2c7'],
  };
  const p = themes[theme] || themes.coast;
  ctx.save(); ctx.translate(x,y);
  ctx.fillStyle = gradient(ctx,0,0,w*.3,h,[[0,p[0]],[.45,p[1]],[1,p[2]]]);
  ctx.fillRect(0,0,w,h);
  const light=ctx.createRadialGradient(w*.23,h*.25,0,w*.23,h*.25,w*.54);
  light.addColorStop(0,p[5]);light.addColorStop(.33,p[5]+'a8');light.addColorStop(1,p[5]+'00');
  ctx.fillStyle=light;ctx.fillRect(0,0,w,h);
  // Broad, softly receding contours keep the original wallpaper quiet behind the window.
  ctx.beginPath();ctx.moveTo(0,h*.57);
  ctx.bezierCurveTo(w*.18,h*.42,w*.36,h*.38,w*.60,h*.47);
  ctx.bezierCurveTo(w*.76,h*.55,w*.93,h*.39,w,h*.37);
  ctx.lineTo(w,h);ctx.lineTo(0,h);ctx.closePath();
  ctx.fillStyle=gradient(ctx,0,h*.4,0,h,[[0,p[2]+'40'],[1,p[2]]]);ctx.fill();
  ctx.beginPath();ctx.moveTo(w,h*.40);
  ctx.bezierCurveTo(w*.82,h*.37,w*.72,h*.57,w*.55,h*.63);
  ctx.bezierCurveTo(w*.32,h*.72,w*.20,h*.62,0,h*.88);
  ctx.lineTo(0,h);ctx.lineTo(w,h);ctx.closePath();
  ctx.fillStyle=gradient(ctx,w*.8,h*.4,w*.32,h,[[0,p[2]],[.52,p[3]],[1,p[4]]]);ctx.fill();
  ctx.beginPath();ctx.moveTo(0,h*.92);
  ctx.bezierCurveTo(w*.16,h*.75,w*.38,h*.79,w*.59,h*.78);
  ctx.bezierCurveTo(w*.81,h*.75,w*.86,h*.68,w,h*.56);
  ctx.lineTo(w,h);ctx.lineTo(0,h);ctx.closePath();
  ctx.fillStyle=gradient(ctx,0,h*.75,w,h,[[0,p[4]],[.65,p[3]],[1,p[2]]]);ctx.fill();
  // One fine reflected contour supplies texture without noisy grain or a raster dependency.
  ctx.beginPath();ctx.moveTo(0,h*.925);
  ctx.bezierCurveTo(w*.20,h*.78,w*.39,h*.82,w*.61,h*.80);
  ctx.bezierCurveTo(w*.82,h*.78,w*.92,h*.65,w,h*.60);
  ctx.strokeStyle='#d5eff014';ctx.lineWidth=Math.max(1,w/950);ctx.stroke();
  ctx.restore();
}

function folder(ctx, x, y, size = 42, color = '#69bbec') {
  rounded(ctx,x+2,y+2,size*.44,size*.21,size*.07,'#afdff4');
  rounded(ctx,x,y+size*.13,size,size*.68,size*.1,color);
  rounded(ctx,x+1,y+size*.19,size-2,size*.60,size*.09,gradient(ctx,x,y,x,y+size,[[0,'#9edcfa'],[1,color]]),'#ffffff3b');
}
function dockIcon(ctx, x, y, kind, label, active = false) {
  const size = 53;
  ctx.save();
  ctx.shadowColor='#102a3926';ctx.shadowBlur=5;ctx.shadowOffsetY=2;
  const colors={files:['#8bdaff','#3487e6'],browser:['#fdfefe','#d8eefb'],photos:['#fff','#eef1f5'],notes:['#ffd762','#f4bd3f'],calendar:['#fff','#edf0f5'],music:['#ff808f','#ed4869'],settings:['#dce1e7','#9cabbc']};
  const pair=colors[kind];
  rounded(ctx,x,y,size,size,12,gradient(ctx,x,y,x+size*.7,y+size,[[0,pair[0]],[1,pair[1]]]),'#ffffff62');
  ctx.shadowColor='transparent';
  ctx.save();ctx.beginPath();ctx.roundRect(x,y,size,size,12);ctx.clip();
  if(kind==='files') {
    ctx.fillStyle='#c8f2ff';ctx.fillRect(x+size/2,y,size/2,size);
    stroke(ctx,[[x+30,y+3],[x+23,y+31],[x+33,y+31],[x+32,y+50]],'#2173af',1.6);
    circle(ctx,x+15,y+20,1.6,'#164e7c');circle(ctx,x+37,y+20,1.6,'#164e7c');
    ctx.beginPath();ctx.arc(x+26,y+26,13,.25,Math.PI-.25);ctx.strokeStyle='#164e7c';ctx.lineWidth=1.5;ctx.stroke();
  } else if(kind==='browser') {
    circle(ctx,x+26.5,y+26.5,21,'#349cdd');circle(ctx,x+26.5,y+26.5,18,'#75cbef');
    for(let n=0;n<12;n++){const a=n*Math.PI/6;stroke(ctx,[[x+26.5+Math.sin(a)*16,y+26.5+Math.cos(a)*16],[x+26.5+Math.sin(a)*18,y+26.5+Math.cos(a)*18]],'#ffffffbb',1);}
    ctx.beginPath();ctx.moveTo(x+33,y+11);ctx.lineTo(x+29,y+30);ctx.lineTo(x+20,y+26);ctx.closePath();ctx.fillStyle='#ef726b';ctx.fill();
    ctx.beginPath();ctx.moveTo(x+20,y+42);ctx.lineTo(x+24,y+23);ctx.lineTo(x+33,y+27);ctx.closePath();ctx.fillStyle='#fff';ctx.fill();
  } else if(kind==='photos') {
    ['#ec7890','#ed9963','#e9c352','#a9c966','#70bca4','#65b4d4','#8b98d4','#c691c3'].forEach((color,index)=>{
      const a=index*Math.PI/4;ctx.save();ctx.translate(x+26.5,y+26.5);ctx.rotate(a);ctx.globalAlpha=.86;
      ctx.beginPath();ctx.ellipse(0,-11,6.5,11,0,0,Math.PI*2);ctx.fillStyle=color;ctx.fill();ctx.restore();
    });circle(ctx,x+26.5,y+26.5,5,'#fffcdf');
  } else if(kind==='notes') {
    ctx.fillStyle='#fffdfa';ctx.fillRect(x,y+16,size,size-16);
    [26,34,42].forEach(line=>stroke(ctx,[[x+10,y+line],[x+43,y+line]],'#c8c6bf',1));
    stroke(ctx,[[x,y+18],[x+size,y+18]],'#e7cfa2',1);
  } else if(kind==='calendar') {
    type(ctx,label,x+26.5,y+16,9,'#e25c59',650,'center');type(ctx,'12',x+26.5,y+43,28,'#303740',350,'center');
  } else if(kind==='music') {
    stroke(ctx,[[x+23,y+37],[x+23,y+15],[x+39,y+11],[x+39,y+33]],'#fff',3.5);
    ctx.beginPath();ctx.ellipse(x+18,y+38,7,5,-.2,0,Math.PI*2);ctx.ellipse(x+34,y+34,7,5,-.2,0,Math.PI*2);ctx.fillStyle='#fff';ctx.fill();
  } else {
    ctx.save();ctx.translate(x+26.5,y+26.5);
    for(let n=0;n<12;n++){ctx.rotate(Math.PI/6);rounded(ctx,-3.1,-21,6.2,9,1.5,'#67798b');}
    circle(ctx,0,0,17,'#67798b');circle(ctx,0,0,12,'#d9e1e8');circle(ctx,0,0,7,'#8293a5');circle(ctx,0,0,4,'#cdd8e2');ctx.restore();
  }
  ctx.restore();
  if(active) circle(ctx,x+26.5,y+size+8,2,'#e8f3f3b8');
  ctx.restore();
}

function finder(ctx, x, y, w, h, en) {
  ctx.save();
  ctx.shadowColor='#102d3d38';ctx.shadowBlur=45;ctx.shadowOffsetY=22;
  rounded(ctx,x,y,w,h,13,'#f8fafbf5');ctx.shadowColor='transparent';
  ctx.save();ctx.beginPath();ctx.roundRect(x,y,w,h,13);ctx.clip();
  const sidebar=158;
  ctx.fillStyle='#e7edf1ec';ctx.fillRect(x,y,sidebar,h);
  ctx.fillStyle='#f1f4f6';ctx.fillRect(x+sidebar,y,w-sidebar,54);
  stroke(ctx,[[x+sidebar,y+54],[x+w,y+54]],'#d8e0e666',1);
  [['#ed786e',0],['#efc25b',19],['#70bd89',38]].forEach(([color,offset])=>circle(ctx,x+22+offset,y+26,5.5,color));
  stroke(ctx,[[x+sidebar+24,y+22],[x+sidebar+19,y+27],[x+sidebar+24,y+32]],'#73818c',1.8);
  stroke(ctx,[[x+sidebar+45,y+22],[x+sidebar+50,y+27],[x+sidebar+45,y+32]],'#b1bac2',1.8);
  type(ctx,en?'Light collection':'光影收藏',x+sidebar+70,y+32,14,'#38434f',600);
  const searchX=x+w-34;
  ctx.beginPath();ctx.arc(searchX,y+25,5.5,0,Math.PI*2);ctx.strokeStyle='#81909b';ctx.lineWidth=1.5;ctx.stroke();
  stroke(ctx,[[searchX+4,y+29],[searchX+8,y+33]],'#81909b',1.5);
  type(ctx,en?'FAVORITES':'个人收藏',x+18,y+82,9,'#8c99a5',600);
  const rows = en ? ['Recents','Desktop','Pictures','Downloads'] : ['最近使用','桌面','图片','下载'];
  rows.forEach((row,index)=>{
    const yy=y+109+index*32;
    if(index===2)rounded(ctx,x+8,yy-20,sidebar-16,28,6,'#d0deea');
    if(index===0){ctx.beginPath();ctx.arc(x+24,yy-7,6,0,Math.PI*2);ctx.strokeStyle='#6a9eb8';ctx.lineWidth=1.4;ctx.stroke();stroke(ctx,[[x+24,yy-11],[x+24,yy-7],[x+27,yy-5]],'#6a9eb8',1.4);}
    else if(index===1)rounded(ctx,x+17,yy-13,14,10,2,null,'#6a9eb8');
    else if(index===2){rounded(ctx,x+17,yy-14,14,12,2,null,'#6a9eb8');stroke(ctx,[[x+19,yy-4],[x+23,yy-9],[x+26,yy-6],[x+29,yy-10]],'#6a9eb8',1.2);}
    else{stroke(ctx,[[x+24,yy-14],[x+24,yy-4],[x+20,yy-8]],'#6a9eb8',1.4);stroke(ctx,[[x+24,yy-4],[x+28,yy-8]],'#6a9eb8',1.4);}
    type(ctx,row,x+39,yy-3,12,'#566574',450);
  });
  type(ctx,en?'COLLECTIONS':'我的收藏',x+18,y+270,9,'#8c99a5',600);
  circle(ctx,x+24,y+294,4,'#8cb5b5');type(ctx,en?'Quiet moments':'静谧时刻',x+39,y+298,12,'#667483');
  const contentX=x+sidebar+29, contentW=w-sidebar-58;
  type(ctx,en?'A little perspective.':'换个角度，看见光。',contentX,y+96,24,'#293d4b',550);
  type(ctx,en?'Three places to pause. Yours to explore.':'收藏片刻宁静，让光影自在流动。',contentX,y+122,12,'#84919a');
  const gap=17,thumbW=(contentW-gap*2)/3,thumbH=thumbW*1.03,thumbY=y+154;
  const labels=en?['Morning coast','Desert dusk','Blue hour']:['海岸晨光','沙丘日落','蓝色时刻'];
  ['coast','dune','night'].forEach((theme,index)=>{
    const xx=contentX+index*(thumbW+gap);
    ctx.save();ctx.beginPath();ctx.roundRect(xx,thumbY,thumbW,thumbH,7);ctx.clip();landscape(ctx,xx,thumbY,thumbW,thumbH,theme);ctx.restore();
    type(ctx,labels[index],xx+thumbW/2,thumbY+thumbH+23,11,'#596875',450,'center');
    type(ctx,en?'Wallpaper':'桌面壁纸',xx+thumbW/2,thumbY+thumbH+41,9,'#a1aab2',400,'center');
  });
  stroke(ctx,[[x+sidebar,y+h-30],[x+w,y+h-30]],'#d9e1e75e',1);
  type(ctx,en?'3 items':'3 个项目',x+sidebar+(w-sidebar)/2,y+h-11,10,'#99a4ad',400,'center');
  ctx.restore();rounded(ctx,x+.5,y+.5,w-1,h-1,13,null,'#ffffff89');ctx.restore();
}

/** Paint a bilingual, original desktop at the canvas's current pixel dimensions. */
export function drawDesktop(ctx, width, height, {lang = 'zh', wall = 'coast'} = {}) {
  if(!width || !height) return;
  const en=lang==='en';
  const W=1440, H=900;
  ctx.save();
  ctx.scale(width/W,height/H);
  landscape(ctx,0,0,W,H,wall);
  // A translucent native-style menu strip with our own product mark.
  ctx.fillStyle='#eff7f84d';ctx.fillRect(0,0,W,30);
  ctx.save();ctx.translate(23,15);ctx.rotate(-.18);
  rounded(ctx,-7,-6,5,12,2,'#385d65');rounded(ctx,0,-6,5,12,2,'#385d65');ctx.restore();
  type(ctx,'Duo',46,20,12,'#2e515d',650);
  const menu=en?['File','Edit','View','Go','Window','Help']:['文件','编辑','显示','前往','窗口','帮助'];
  let menuX=91;
  menu.forEach(item=>{type(ctx,item,menuX,20,11,'#43636d',450);menuX+=en?item.length*6.5+24:50;});
  // Simple status symbols drawn as vectors instead of platform-dependent emoji.
  [3,6,9].forEach(radius=>{ctx.beginPath();ctx.arc(W-198,19,radius,Math.PI*1.18,Math.PI*1.82);ctx.strokeStyle='#44616b';ctx.lineWidth=1.4;ctx.stroke();});circle(ctx,W-198,20,1.3,'#44616b');
  rounded(ctx,W-174,10,22,10,3,null,'#55717b');rounded(ctx,W-172,12,16,6,1,'#55717b');rounded(ctx,W-150,13,2,4,1,'#55717b');
  type(ctx,en?'Sat  9:41':'周六  9:41',W-25,20,11,'#3f5c66',500,'right');
  // The window is slightly off-centre so the landscape has room to breathe.
  finder(ctx,369,171,747,462,en);
  folder(ctx,1321,75,54);type(ctx,en?'Studio':'创作',1348,136,12,'#f7ffff',500,'center');
  // A restrained translucent shelf, sized to its contents.
  const dockW=7*53+6*16+26, dockX=(W-dockW)/2, dockY=H-94;
  ctx.save();ctx.shadowColor='#14313f20';ctx.shadowBlur=20;ctx.shadowOffsetY=5;
  rounded(ctx,dockX,dockY,dockW,78,21,'#e8f4f34d','#e4ffff55');ctx.restore();
  ['files','browser','photos','notes','calendar','music','settings'].forEach((kind,index)=>dockIcon(ctx,dockX+13+index*69,dockY+9,kind,en?'SAT':'周六',index===0));
  ctx.restore();
}
