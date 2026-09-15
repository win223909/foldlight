import test from 'node:test';
import assert from 'node:assert/strict';
import {desktopOptics} from '../desktop-optics.js';

const identity = [1, 0, 0, 0, 1, 0, 0, 0, 1];
const geometry = {width:606, height:403, borderLeft:7, borderRight:9, borderTop:7, borderBottom:12, perspective:6000, gap:4};
function near(actual, expected, message = '') {
  assert.ok(Math.abs(actual - expected) < 1e-8, `${message}: ${actual} != ${expected}`);
}
function map(matrix, [u, v]) {
  const z = matrix[2] * u + matrix[5] * v + matrix[8];
  return [(matrix[0] * u + matrix[3] * v + matrix[6]) / z,
    (matrix[1] * u + matrix[4] * v + matrix[7]) / z];
}
// Independent reference: rotate a point around the actual outer-frame hinge,
// then project its three-dimensional position through the CSS camera.
function observer(g, angle, [u, v]) {
  const width = g.width + g.borderLeft + g.borderRight;
  const height = g.height + g.borderTop + g.borderBottom;
  const x = g.borderLeft + u * g.width, y = g.borderTop + v * g.height;
  const theta = angle * Math.PI / 180;
  const rotatedY = (y - height) * Math.cos(theta);
  const z = (y - height) * Math.sin(theta) + g.gap;
  const scale = g.perspective === 0 ? 1 : g.perspective / (g.perspective - z);
  return [width / 2 + (x - width / 2) * scale, height + rotatedY * scale];
}
function planePointAtObserver(g, angle, [x, y]) {
  const width = g.width + g.borderLeft + g.borderRight;
  const height = g.height + g.borderTop + g.borderBottom;
  const theta = angle * Math.PI / 180, delta = y - height;
  const relativeY = g.perspective === 0 ? delta / Math.cos(theta) :
    delta * (g.perspective - g.gap) / (g.perspective * Math.cos(theta) + delta * Math.sin(theta));
  const scale = g.perspective === 0 ? 1 : g.perspective / (g.perspective - g.gap - relativeY * Math.sin(theta));
  return [(width / 2 + (x - width / 2) / scale - g.borderLeft) / g.width,
    (height + relativeY - g.borderTop) / g.height];
}

test('open desktop is exactly unchanged, including asymmetric bezels and lid gap', () => {
  for (const perspective of [0, 6000]) {
    const result = desktopOptics({...geometry, angle:0, perspective});
    assert.deepEqual(result.projection, identity);
    near(result.depth[0], 0);near(result.depth[1], 0);
    assert.equal(result.frontFacing, true);
  }
});

test('texture compensation matches an independent camera projection across the screen', () => {
  for (const perspective of [0, 6000]) {
    const g = {...geometry, perspective};
    for (const angle of [0, -15, -35, -55, -75]) {
      const {projection} = desktopOptics({...g, angle});
      for (const u of [0, .25, .5, .75, 1]) for (const v of [0, .25, .5, .75, 1]) {
        const actual = observer(g, 0, map(projection, [u, v]));
        const expected = observer(g, angle, [u, v]);
        near(actual[0], expected[0], 'observer x');near(actual[1], expected[1], 'observer y');
      }
    }
  }
});

test('visible fixed markers retain their observer position as the lid closes', () => {
  for (const perspective of [0, 6000]) {
    const g = {...geometry, perspective};
    let checked = 0;
    for (const angle of [0, -15, -35, -55, -75]) {
      const {projection} = desktopOptics({...g, angle});
      for (const marker of [[.2, .85], [.5, .95], [.8, .98]]) {
        const target = observer(g, 0, marker);
        const local = planePointAtObserver(g, angle, target);
        if (local.some(value => value < 0 || value > 1)) continue;
        const sample = map(projection, local);
        near(sample[0], marker[0], 'fixed marker u');near(sample[1], marker[1], 'fixed marker v');
        const painted = observer(g, angle, local);
        near(painted[0], target[0]);near(painted[1], target[1]);checked++;
      }
    }
    assert.ok(checked >= 12, 'the test must cover markers through the closing travel');
  }
});

test('normalizing geometry to CSS pixels makes projection independent of render resolution', () => {
  const baseline = desktopOptics({...geometry, angle:-55});
  // Equivalently, changing every spatial unit from CSS pixels to raster pixels
  // must leave the dimensionless transform/depth unchanged.
  for (const scale of [1, 2, 3]) {
    const g = Object.fromEntries(Object.entries(geometry).map(([key, value]) => [key, value * scale]));
    const result = desktopOptics({...g, angle:-55});
    result.projection.forEach((value, index) => near(value, baseline.projection[index]));
    result.depth.forEach((value, index) => near(value, baseline.depth[index]));
  }
});

test('depth increases continuously away from the hinge and through the visible travel', () => {
  let previous = [-1, -1, -1];
  for (let angle = 0; angle <= 90; angle += .5) {
    const {depth} = desktopOptics({...geometry, angle:-angle});
    const values = [0, .5, 1].map(v => depth[0] + depth[1] * v);
    assert.ok(values[0] >= values[1] && values[1] >= values[2] && values[2] >= 0);
    values.forEach((value, index) => {assert.ok(value >= previous[index] - 1e-12);assert.ok(value - previous[index] < .01 || previous[index] === -1);});
    previous = values;
  }
  const edge = desktopOptics({...geometry, angle:-90});
  const back = desktopOptics({...geometry, angle:-125});
  assert.deepEqual(back.depth, edge.depth);
  assert.equal(edge.frontFacing, false);assert.equal(back.frontFacing, false);
  assert.equal(desktopOptics({...geometry, angle:-89.9}).frontFacing, true);
  const noBezel = desktopOptics({angle:-55, width:300, height:200});
  near(noBezel.depth[0] + noBezel.depth[1], 0, 'hinge stays on the focus plane');
});

test('immersive mode never compensates a CSS transform that is not present', () => {
  for (const angle of [0, -35, -75, -125]) {
    const result = desktopOptics({...geometry, angle, immersed:true});
    assert.deepEqual(result.projection, identity);
    assert.deepEqual(result.depth, desktopOptics({...geometry, angle}).depth);
  }
});

test('invalid dimensions and camera singularities cannot emit invalid uniforms', () => {
  const valid = {...geometry, angle:-35};
  for (const invalid of [{width:0}, {width:NaN}, {height:-1}, {angle:Infinity}, {gap:NaN},
    {borderTop:-1}, {borderBottom:Infinity}, {perspective:-1}, {perspective:4},
    {angle:-90, perspective:419}, {angle:-90, perspective:419.00001}]) {
    assert.throws(() => desktopOptics({...valid, ...invalid}), RangeError);
  }
  for (const angle of [0, -30, -89.9, -90, -125]) {
    const {projection, depth} = desktopOptics({...valid, angle});
    assert.ok([...projection, ...depth].every(Number.isFinite));
  }
});
