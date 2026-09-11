import test from 'node:test';import assert from 'node:assert/strict';
import {deviceKind,isMac,motionValue} from '../capabilities.js';
test('iPad desktop UA is a touch device, Mac and Windows are desktop',()=>{
 const ipad={platform:'MacIntel',maxTouchPoints:5};assert.equal(deviceKind(ipad),'phone');assert.equal(isMac(ipad),false);
 assert.equal(isMac({platform:'MacIntel'}),true);assert.equal(isMac({platform:'Win32'}),false);
 assert.equal(deviceKind({userAgent:'Android'}),'phone');assert.equal(deviceKind({userAgent:'iPhone'}),'phone');
});
test('orientation follows screen rotation and rejects missing readings',()=>{
 assert.equal(motionValue({gamma:10,beta:20}),10);assert.equal(motionValue({gamma:10,beta:20},90),20);
 assert.equal(motionValue({gamma:10,beta:20},270),-20);assert.equal(motionValue({gamma:null}),null);
 assert.equal(motionValue({gamma:NaN}),null);
});
