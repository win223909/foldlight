import test from 'node:test';import assert from 'node:assert/strict';import {clampAngle} from '../angles.js';
test('phone tilt is bounded to plus or minus 90 degrees',()=>{assert.equal(clampAngle(-120),-90);assert.equal(clampAngle(120),90);assert.equal(clampAngle('25'),25);assert.equal(clampAngle(NaN),0)});
