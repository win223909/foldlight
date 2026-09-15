import test from 'node:test';
import assert from 'node:assert/strict';
import {clampPreviewAngle, scrollPreviewAngle} from '../presentation.js';

test('desktop preview only closes, while phone retains both tilt directions', () => {
  assert.equal(clampPreviewAngle(40, 'desktop'), 0);
  assert.equal(clampPreviewAngle(-160, 'desktop'), -120);
  assert.equal(clampPreviewAngle(-95, 'desktop'), -95);
  assert.equal(clampPreviewAngle(120, 'phone'), 90);
  assert.equal(clampPreviewAngle(-120, 'phone'), -90);
  assert.equal(clampPreviewAngle(NaN, 'desktop'), 0);
});

test('scroll preview stays continuous, bounded and monotonic across its full travel', () => {
  assert.equal(scrollPreviewAngle(-1), 0);
  assert.equal(scrollPreviewAngle(2), -120);
  let previous = 0;
  for (let p = 0; p <= 1; p += .01) {
    const next = scrollPreviewAngle(p);
    assert.ok(next <= previous && previous - next < 2);
    previous = next;
  }
});

test('scroll waits for the actual device center before folding', async () => {
  const {centeredFoldScroll}=await import('../presentation.js');
  const layout={anchorTop:410,deviceHeight:520,viewportHeight:900,navHeight:60,travel:700};
  const before=centeredFoldScroll({...layout,scrollY:219});
  const centered=centeredFoldScroll({...layout,scrollY:220});
  assert.equal(before.progress,0);
  assert.equal(centered.progress,0);
  assert.equal(layout.anchorTop-centered.start+layout.deviceHeight/2,layout.viewportHeight/2);
  assert.equal(centeredFoldScroll({...layout,scrollY:570}).progress,.5);
  assert.equal(centeredFoldScroll({...layout,scrollY:920}).progress,1);
});
