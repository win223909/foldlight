import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import vm from 'node:vm';

const startup=readFileSync(new URL('../view-init.js',import.meta.url),'utf8');
function start({ua='Macintosh',platform='MacIntel',touch=0,language='en-US',saved=null,blocked=false,assets=true}={}) {
  const links=[],document={documentElement:{dataset:{}},
    currentScript:{getAttribute:name=>assets?`/${name}.webp`:null},
    createElement:()=>({}),head:{append:link=>links.push(link)}};
  vm.runInNewContext(startup,{document,
    navigator:{userAgent:ua,platform,maxTouchPoints:touch,language},
    localStorage:{getItem:()=>{if(blocked)throw Error('denied');return saved}}});
  return {view:document.documentElement.dataset.view,links};
}
test('preload selects exactly one image for device and persisted language',()=>{
  const phone=start({ua:'iPhone',saved:'zh'});
  assert.equal(phone.view,'phone');
  assert.deepEqual(phone.links.map(l=>l.href),['/data-phone-zh.webp']);
  assert.equal(phone.links[0].fetchPriority,'high');
  assert.deepEqual(start().links.map(l=>l.href),['/data-desktop-en.webp']);
  assert.equal(start({touch:5}).view,'phone');
});
test('private storage denial and builds without private image assets still start',()=>{
  assert.deepEqual(start({blocked:true,language:'zh-CN'}).links.map(l=>l.href),['/data-desktop-zh.webp']);
  assert.equal(start({assets:false}).links.length,0);
});
