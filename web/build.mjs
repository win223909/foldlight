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
// Hash dependencies first; then hash the rewritten entry module for the HTML.
let app=files.get('app.js').toString();
for(const name of ['angles.js','animation.js','glass-blur.js','capabilities.js','screen-layout.js','assets/demo-screen.svg'])app=app.replaceAll(`./${name}`,versioned(name));
files.set('app.js',Buffer.from(app));
let html=files.get('index.html').toString();
for(const name of ['app.js','style.css','icon.svg','assets/demo-screen.svg'])html=html.replaceAll(`./${name}`,versioned(name));
files.set('index.html',Buffer.from(html));
files.set('LICENSE.txt',await readFile(path.resolve(root,'../LICENSE')));
await rm(out,{recursive:true,force:true});
const manifest={};
for(const [name,data]of files){await mkdir(path.dirname(path.join(out,name)),{recursive:true});await writeFile(path.join(out,name),data);manifest[name]=hash(data)}
await writeFile(path.resolve(out,'../duo-static-manifest.json'),JSON.stringify(manifest,null,2)+'\n');
console.log(`Built ${files.size} files with content-versioned URLs: ${out}`);
