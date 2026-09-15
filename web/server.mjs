import {publicFiles} from './files.mjs';
import http from 'node:http';
import {readFile} from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const root=path.dirname(fileURLToPath(import.meta.url));
const port=Number(process.env.FOLDLIGHT_PORT||4178);
const files=new Set(publicFiles);
const overrides=new Map([
  ['assets/demo-screen.svg',process.env.FOLDLIGHT_DEMO_IMAGE],
  ['assets/phone-zh.png',process.env.FOLDLIGHT_PHONE_ZH],
  ['assets/phone-en.png',process.env.FOLDLIGHT_PHONE_EN],
  ['assets/desktop-zh.png',process.env.FOLDLIGHT_DESKTOP_ZH],
  ['assets/desktop-en.png',process.env.FOLDLIGHT_DESKTOP_EN],
  ['assets/macbook-deck-reference.jpg',process.env.FOLDLIGHT_MACBOOK_DECK],
  ['assets/macbook-lid-reference.jpg',process.env.FOLDLIGHT_MACBOOK_LID]
].filter(([,value])=>value));
const desktopReady=overrides.has('assets/desktop-zh.png')&&overrides.has('assets/desktop-en.png');
const phoneReady=overrides.has('assets/phone-zh.png')&&overrides.has('assets/phone-en.png');
const types={jpg:'image/jpeg',jpeg:'image/jpeg',webp:'image/webp',avif:'image/avif',apk:'application/vnd.android.package-archive',dmg:'application/octet-stream',txt:'text/plain; charset=utf-8',sha256:'text/plain; charset=utf-8',png:'image/png',html:'text/html; charset=utf-8',js:'text/javascript; charset=utf-8',css:'text/css; charset=utf-8',svg:'image/svg+xml'};

http.createServer(async(req,res)=>{
  let name;
  try{name=new URL(req.url,'http://localhost').pathname.slice(1)||'index.html'}
  catch{res.writeHead(400);res.end();return}
  if(!['GET','HEAD'].includes(req.method)||(!files.has(name)&&!overrides.has(name))){
    res.writeHead(404);res.end();return;
  }
  try{
    const override=overrides.get(name);
    const data=name==='screen-assets.js'&&(desktopReady||phoneReady)
      ? `export const desktopScreens=${desktopReady?JSON.stringify({zh:'./assets/desktop-zh.png',en:'./assets/desktop-en.png'}):'null'};export const phoneScreens=${phoneReady?JSON.stringify({zh:'./assets/phone-zh.png',en:'./assets/phone-en.png'}):'null'};`
      : await readFile(override||path.join(root,name));
    res.writeHead(200,{'Content-Type':types[(override||name).split('.').pop()],'Cache-Control':'no-store','X-Content-Type-Options':'nosniff'});
    res.end(req.method==='HEAD'?undefined:data);
  }catch{res.writeHead(404);res.end()}
}).listen(port,'127.0.0.1',()=>console.log(`Duo: http://127.0.0.1:${port}`));
