import test from 'node:test';
import assert from 'node:assert/strict';
import {screenLayout,isPhoneScreenshot} from '../screen-layout.js';

test('installed iPhone with a system-reserved top fits screenshot without cutting off Dock',()=>{
 const r=screenLayout({iw:1320,ih:2868,w:440,h:894,immersed:true,installed:true,hasStatus:true});
 assert.equal(r.sy,201);assert.ok(r.y>=0);assert.ok(r.y+r.height<=894);
 assert.ok(Math.abs(r.width/r.height-r.sw/r.sh)<1e-10);
});
test('translucent status bar leaves only safe-area space above image content',()=>{
 const r=screenLayout({iw:1320,ih:2868,w:440,h:956,immersed:true,installed:true,hasStatus:true,safeTop:62});
 assert.ok(r.y>=62);assert.ok(r.y+r.height<=956);assert.ok(r.x>=0);
});
test('photos and ordinary browser previews retain their status-area pixels',()=>{
 for(const options of [{immersed:true,installed:true,hasStatus:false},{immersed:true,installed:false,hasStatus:true},{immersed:false,installed:true,hasStatus:true}]){
  const r=screenLayout({iw:1320,ih:2868,w:390,h:844,...options});assert.equal(r.sy,0);
 }
});
test('landscape fullscreen contains the whole portrait image without stretching',()=>{
 const r=screenLayout({iw:1206,ih:2622,w:844,h:390,immersed:true});
 assert.equal(r.height,390);assert.ok(r.width<844);assert.equal(r.sy,0);
});
test('only near-device portrait aspect ratios suggest screenshot adaptation',()=>{
 assert.equal(isPhoneScreenshot(1320,2868,440,956),true);
 assert.equal(isPhoneScreenshot(4032,3024,440,956),false);
 assert.equal(isPhoneScreenshot(3000,4000,440,956),false);
});
