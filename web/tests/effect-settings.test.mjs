import test from 'node:test';
import assert from 'node:assert/strict';
import {effectDefaults,nextBrightness} from '../effect-settings.js';
test('recommended glass defaults and full brightness travel',()=>{
 assert.equal(effectDefaults.blur,1);assert.equal(effectDefaults.compression,.6);
 let level=1;
 for(let a=0;a<=90;a++){let next=nextBrightness(level,a);assert.ok(next<=level);if(a<=40)assert.equal(next,1);level=next;}
 assert.equal(level,0);
 for(let a=90;a>=0;a--){let next=nextBrightness(level,a);assert.ok(next>=level);if(a<=20)assert.equal(next,1);level=next;}
 assert.equal(level,1);
});
test('stationary angles and direction reversals preserve brightness',()=>{
 let level=nextBrightness(1,60);assert.equal(nextBrightness(level,60),level);
 assert.equal(nextBrightness(level,59.99),level);
 assert.equal(nextBrightness(1,-60),nextBrightness(1,60));
});
