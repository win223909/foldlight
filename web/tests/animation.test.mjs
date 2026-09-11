import test from 'node:test';
import assert from 'node:assert/strict';
import {smoothAngle} from '../animation.js';
test('60 and 120 Hz converge equally over the same real time',()=>{
 const advance=hz=>{let angle=0;for(let i=0;i<hz/10;i++)angle=smoothAngle(angle,-120,1000/hz);return angle;};
 assert.ok(Math.abs(advance(60)-advance(120))<1e-9);
 assert.ok(advance(120)<-116);
});
test('damping never overshoots when closing, reopening or frames stall',()=>{
 for(const [start,end] of [[0,-120],[-120,0]])for(const dt of [0,8.33,16.67,1000]){
  const angle=smoothAngle(start,end,dt);assert.ok(angle>=-120&&angle<=0);
 }
});
