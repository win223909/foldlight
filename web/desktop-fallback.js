// Cache source and Gaussian scales once per image/resize; resample the Mac
// homography in bounded horizontal bands without a second CSS rotation.
const snapshots = new WeakMap();
const extendedImages = new WeakMap();
const sigmaBase = Math.sqrt(8);
const clamp = (v, min, max) => Math.max(min, Math.min(max, v));
function canvas(w, h) {const c = document.createElement('canvas');c.width = w;c.height = h;return c;}
export function captureDesktopFallback(source) {
  const image = canvas(source.width, source.height);image.getContext('2d').drawImage(source, 0, 0);
  snapshots.set(source, {image, levels:null, boundary:null});
}
function boxPass(input, output, width, height, radius, horizontal) {
  const length = horizontal ? width : height, lines = horizontal ? height : width;
  const stride = horizontal ? 4 : width * 4, divisor = radius * 2 + 1;
  for (let line = 0; line < lines; line++) {
    const start = horizontal ? line * width * 4 : line * 4;
    for (let channel = 0; channel < 4; channel++) {
      let sum = 0;
      for (let i = -radius; i <= radius; i++) sum += input[start + clamp(i, 0, length - 1) * stride + channel];
      for (let i = 0; i < length; i++) {
        output[start + i * stride + channel] = sum / divisor;
        sum += input[start + Math.min(length - 1, i + radius + 1) * stride + channel] - input[start + Math.max(0, i - radius) * stride + channel];
      }
    }
  }
}
function blurLevel(image, width, height) {
  const result = canvas(width, height), context = result.getContext('2d');
  context.imageSmoothingQuality = 'high';context.drawImage(image, 0, 0, width, height);
  if ('filter' in context) {
    // Extend edge texels so transparent canvas edges do not darken the blur.
    const padding = 10, extended = canvas(width + padding * 2, height + padding * 2), e = extended.getContext('2d');
    e.drawImage(result, padding, padding);
    e.drawImage(result, 0, 0, width, 1, padding, 0, width, padding);
    e.drawImage(result, 0, height - 1, width, 1, padding, height + padding, width, padding);
    e.drawImage(extended, padding, 0, 1, extended.height, 0, 0, padding, extended.height);
    e.drawImage(extended, padding + width - 1, 0, 1, extended.height, padding + width, 0, padding, extended.height);
    context.clearRect(0, 0, width, height);context.filter = `blur(${sigmaBase}px)`;
    context.drawImage(extended, -padding, -padding);context.filter = 'none';
  } else {
    // Older Safari: three separable box passes approximate a Gaussian.
    // This work is cached and each level is at most 768 pixels per side.
    const pixels = context.getImageData(0, 0, width, height), scratch = new Uint8ClampedArray(pixels.data.length);
    for (const radius of [2, 2, 3]) {boxPass(pixels.data, scratch, width, height, radius, true);boxPass(scratch, pixels.data, width, height, radius, false);}
    context.putImageData(pixels, 0, 0);
  }
  return result;
}
function levelsFor(snapshot) {
  if (snapshot.levels) return snapshot.levels;
  const {image} = snapshot, levels = [{image, sigma:0}], firstScale = Math.max(1, Math.max(image.width, image.height) / 768);
  for (let level = 0; level < 8; level++) {
    const scale = firstScale * 2 ** level, width = Math.max(1, Math.round(image.width / scale)), height = Math.max(1, Math.round(image.height / scale));
    levels.push({image:blurLevel(image, width, height), sigma:sigmaBase * scale});
    if (Math.max(width, height) <= 4) break;
  }
  snapshot.levels = levels;return levels;
}
function uvAt(m, u, v) {
  const z = m[2] * u + m[5] * v + m[8];
  return [(m[0] * u + m[3] * v + m[6]) / z, (m[1] * u + m[4] * v + m[7]) / z];
}
function drawBand(context, image, left, right, top, bottom, width, y, height) {
  let sy = clamp(top * image.height, 0, image.height), ey = clamp(bottom * image.height, 0, image.height);
  if (ey <= sy) {sy = clamp(sy, 0, image.height - 1);ey = sy + 1;}
  const sourceLeft = left * image.width, sourceWidth = (right - left) * image.width;
  if (sourceWidth <= 0) return;
  const required = Math.ceil(Math.max(-left, right - 1, 0) * image.width) + 1;
  let extended = extendedImages.get(image);
  if (!extended || extended.padding < required) {
    // Cache clamped texels alongside the image. A band is sampled in one draw,
    // avoiding subpixel joins between three separately blended edge pieces.
    const quantum = Math.max(1, Math.ceil(image.width / 8)), padding = Math.ceil(Math.max(required, quantum) / quantum) * quantum;
    const padded = canvas(image.width + padding * 2, image.height), p = padded.getContext('2d');
    p.drawImage(image, padding, 0);
    p.drawImage(image, 0, 0, 1, image.height, 0, 0, padding, image.height);
    p.drawImage(image, image.width - 1, 0, 1, image.height, padding + image.width, 0, padding, image.height);
    extended = {image:padded, padding};extendedImages.set(image, extended);
  }
  context.drawImage(extended.image, sourceLeft + extended.padding, sy, sourceWidth, ey - sy, 0, y, width, height);
}
function boundaryFor(snapshot, projection, depth, amount) {
  const width = Math.min(512, snapshot.image.width), height = Math.min(384, snapshot.image.height);
  const key = [...projection, ...depth, amount].join(',');
  let boundary = snapshot.boundary;
  if (boundary?.key === key) return boundary.image;
  if (!boundary) {
    const image = canvas(width, height), context = image.getContext('2d');
    boundary = snapshot.boundary = {image, context, pixels:context.createImageData(width, height)};
  }
  const {data} = boundary.pixels, sourceWidth = snapshot.image.width, sourceHeight = snapshot.image.height;
  for (let y = 0; y < height; y++) {
    const v = (y + .5) / height;
    const radius = amount * sourceHeight * .18 * Math.max(0, depth[0] + depth[1] * v), feather = Math.max(.5, radius * .6);
    // The lid's X-axis rotation makes its homogeneous divisor constant per row.
    const divisor = projection[5] * v + projection[8], step = projection[0] / (width * divisor);
    const start = (projection[3] * v + projection[6]) / divisor + step * .5;
    const vHit = (projection[4] * v + projection[7]) / divisor, vertical = Math.min(vHit, 1 - vHit) * sourceHeight;
    for (let x = 0; x < width; x++) {
      const uHit = start + step * x, distance = Math.min(uHit * sourceWidth, (1 - uHit) * sourceWidth, vertical);
      const t = clamp((distance + feather) / (2 * feather), 0, 1);
      // Match desktopScene's smoothstep coverage, composited over opaque black.
      data[(y * width + x) * 4 + 3] = Math.round((1 - t * t * (3 - 2 * t)) * 255);
    }
  }
  boundary.context.putImageData(boundary.pixels, 0, 0);boundary.key = key;return boundary.image;
}
export function renderDesktopFallback(destination, optics, blur, {immersed = false} = {}) {
  if (!snapshots.has(destination)) captureDesktopFallback(destination);
  const snapshot = snapshots.get(destination), context = destination.getContext('2d');
  const {width, height} = destination, {projection, depth} = optics;
  context.save();context.setTransform(1, 0, 0, 1, 0, 0);context.globalAlpha = 1;context.globalCompositeOperation = 'source-over';
  if ('filter' in context) context.filter = 'none';
  context.fillStyle = '#000';context.fillRect(0, 0, width, height);
  if (!optics.frontFacing && !immersed) {context.restore();return;}
  const amount = clamp(Number(blur) || 0, 0, 1);
  if (depth[0] === 0 && depth[1] === 0) {context.drawImage(snapshot.image, 0, 0, width, height);context.restore();return;}
  const levels = amount > 0 ? levelsFor(snapshot) : [{image:snapshot.image, sigma:0}], step = Math.max(1, Math.ceil(height / 192));
  context.imageSmoothingEnabled = true;context.imageSmoothingQuality = 'high';
  for (let y = 0; y < height; y += step) {
    const bandHeight = Math.min(step, height - y), v = (y + bandHeight / 2) / height;
    const left = uvAt(projection, 0, v)[0], right = uvAt(projection, 1, v)[0];
    const top = uvAt(projection, .5, y / height)[1], bottom = uvAt(projection, .5, (y + bandHeight) / height)[1];
    const sigma = amount * snapshot.image.height * .18 * Math.max(0, depth[0] + depth[1] * v);
    let upper = 0;while (upper < levels.length - 1 && levels[upper].sigma < sigma) upper++;
    const low = levels[Math.max(0, upper - 1)], high = levels[upper];
    context.globalAlpha = 1;drawBand(context, low.image, left, right, top, bottom, width, y, bandHeight);
    if (high !== low) {
      context.globalAlpha = clamp((sigma * sigma - low.sigma * low.sigma) / (high.sigma * high.sigma - low.sigma * low.sigma), 0, 1);
      drawBand(context, high.image, left, right, top, bottom, width, y, bandHeight);
    }
  }
  context.globalAlpha = 1;context.drawImage(boundaryFor(snapshot, projection, depth, amount), 0, 0, width, height);
  context.restore();
}
