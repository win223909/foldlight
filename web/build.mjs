import {publicFiles} from './files.mjs';
import {readFile,writeFile,mkdir,rm} from 'node:fs/promises';
import {createHash} from 'node:crypto';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
const root=path.dirname(fileURLToPath(import.meta.url));
const out=path.resolve(root,'../build/WebRelease/duo-static');
const names=publicFiles;
const files=new Map(await Promise.all(names.map(async name=>[name,await readFile(path.join(root,name))])));
const hash=data=>createHash('sha256').update(data).digest('hex');
const versioned=name=>`./${name}?v=${hash(files.get(name)).slice(0,12)}`;
const optionalAssets=[
  ['assets/phone-zh.png','FOLDLIGHT_PHONE_ZH'],
  ['assets/phone-en.png','FOLDLIGHT_PHONE_EN'],
  ['assets/desktop-zh.png','FOLDLIGHT_DESKTOP_ZH'],
  ['assets/desktop-en.png','FOLDLIGHT_DESKTOP_EN'],
  ['assets/macbook-deck-reference.jpg','FOLDLIGHT_MACBOOK_DECK'],
  ['assets/macbook-lid-reference.jpg','FOLDLIGHT_MACBOOK_LID']
];
for(const [zh,en]of [['FOLDLIGHT_PHONE_ZH','FOLDLIGHT_PHONE_EN'],['FOLDLIGHT_DESKTOP_ZH','FOLDLIGHT_DESKTOP_EN']]){
  if(Boolean(process.env[zh])!==Boolean(process.env[en]))throw new Error(`Configure both ${zh} and ${en}, or neither.`);
}
const screenNames=new Map();
for(const [name,key]of optionalAssets){
  if(process.env[key]){
    // Keep modern image formats under their real extension (correct MIME type).
    const ext=path.extname(process.env[key]).toLowerCase();
    const target=name.endsWith('.png')&&['.webp','.avif','.jpg','.jpeg'].includes(ext)
      ? name.replace(/\.png$/,ext):name;
    screenNames.set(name,target);
    files.set(target,await readFile(process.env[key]));
  }
}
// The preview server can supply a custom MIME type for its SVG alias. Static
// servers cannot, so preserve the replacement image's extension in the build.
let demoName='assets/demo-screen.svg';
if(process.env.FOLDLIGHT_DEMO_IMAGE){
  const ext=path.extname(process.env.FOLDLIGHT_DEMO_IMAGE).toLowerCase();
  if(!['.svg','.png','.jpg','.jpeg','.webp','.avif'].includes(ext))throw new Error('FOLDLIGHT_DEMO_IMAGE must be an SVG, PNG, JPEG, WebP or AVIF image.');
  demoName=`assets/demo-screen${ext}`;
  files.set(demoName,await readFile(process.env.FOLDLIGHT_DEMO_IMAGE));
}
const desktopReady=Boolean(process.env.FOLDLIGHT_DESKTOP_ZH);
const phoneReady=Boolean(process.env.FOLDLIGHT_PHONE_ZH);
if(desktopReady||phoneReady){
  const screens=kind=>JSON.stringify(Object.fromEntries(['zh','en'].map(lang=>[lang,versioned(screenNames.get(`assets/${kind}-${lang}.png`))])));
  files.set('screen-assets.js',Buffer.from(`export const desktopScreens=${desktopReady?screens('desktop'):'null'};\nexport const phoneScreens=${phoneReady?screens('phone'):'null'};\n`));
}
let macbookCSS=files.get('macbook.css').toString();
for(const name of ['assets/macbook-deck-reference.jpg','assets/macbook-lid-reference.jpg']){
  if(files.has(name))macbookCSS=macbookCSS.replaceAll(`"/${name}"`,`"${versioned(name)}"`);
}
files.set('macbook.css',Buffer.from(macbookCSS));
files.set('page-motion.js',Buffer.from(files.get('page-motion.js').toString().replaceAll('./presentation.js',versioned('presentation.js'))));
// Hash dependencies first; then hash the rewritten entry module for the HTML.
let app=files.get('app.js').toString();
app=app.replaceAll('./assets/demo-screen.svg',versioned(demoName));
for(const name of ['download-copy.js','desktop-scene.js','desktop-glass.js','desktop-blur.js','desktop-fallback.js','desktop-optics.js','screen-assets.js','page-motion.js','presentation.js','angles.js','animation.js','glass-blur.js','reference-glass.js','effect-settings.js','capabilities.js','screen-layout.js'])app=app.replaceAll(`./${name}`,versioned(name));
files.set('app.js',Buffer.from(app));
let html=files.get('index.html').toString();
const screenHints=[];
for(const kind of ['phone','desktop'])for(const lang of ['zh','en']){
  const name=screenNames.get(`assets/${kind}-${lang}.png`);
  if(name)screenHints.push(`data-${kind}-${lang}="${versioned(name)}"`);
}
html=html.replace('<script src="./view-init.js"',`<script ${screenHints.join(' ')} src="./view-init.js"`);
// Discover dependencies with the entry script instead of another network round trip.
const modules=[...app.matchAll(/^import .*? from ['"](.+?)['"]/gm)].map(match=>match[1]);
html=html.replace('</head>',`${modules.map(url=>`<link rel="modulepreload" href="${url}">`).join('\n')}\n</head>`);
html=html.replaceAll('./assets/demo-screen.svg',versioned(demoName));
for(const name of ['app.js','view-init.js','style.css','macbook.css','icon.svg','apple-touch-icon.png'])html=html.replaceAll(`./${name}`,versioned(name));
files.set('index.html',Buffer.from(html));
files.set('LICENSE.txt',await readFile(path.resolve(root,'../LICENSE')));
await rm(out,{recursive:true,force:true});
const manifest={};
for(const [name,data]of files){await mkdir(path.dirname(path.join(out,name)),{recursive:true});await writeFile(path.join(out,name),data);manifest[name]=hash(data)}
await writeFile(path.resolve(out,'../duo-static-manifest.json'),JSON.stringify(manifest,null,2)+'\n');
console.log(`Built ${files.size} files with content-versioned URLs: ${out}`);
