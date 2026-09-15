/**
 * Compensate a CSS-hinged screen so its texture stays in the open screen's
 * observer coordinates. Dimensions and camera distance are CSS pixels.
 * projection is a column-major mat3; divide its xy result by homogeneous z.
 */
export function desktopOptics({angle, width, height, borderLeft = 0, borderRight = 0,
  borderTop = 0, borderBottom = 0, perspective = 0, gap = 0, immersed = false}) {
  if (![angle, width, height, borderLeft, borderRight, borderTop, borderBottom, perspective, gap].every(Number.isFinite) ||
      width <= 0 || height <= 0 || Math.min(borderLeft, borderRight, borderTop, borderBottom, perspective) < 0) {
    throw new RangeError('Invalid desktop optics geometry');
  }
  const radians = (angle % 360) * Math.PI / 180;
  const sine = Math.sin(radians), cosine = Math.cos(radians);
  const frontFacing = cosine > 1e-8;
  // The hinge is below the image by the bottom bezel, not at its last texel.
  const pivot = 1 + borderBottom / height;
  const distance = Math.sin(Math.min(Math.abs(angle), 90) * Math.PI / 180);
  const depth = [pivot * distance, distance === 0 ? 0 : -distance];
  let projection = [1, 0, 0, 0, 1, 0, 0, 0, 1];
  if (!immersed) {
    if (perspective === 0) {
      projection = [1, 0, 0, 0, cosine, 0, 0, pivot * (1 - cosine), 1];
    } else {
      const openDistance = perspective - gap;
      const denominator = openDistance + (height + borderBottom) * sine;
      const slope = -height * sine;
      if (openDistance <= 0 || Math.min(denominator, denominator + slope) <= openDistance * 1e-6) {
        throw new RangeError('Desktop screen reaches the camera projection limit');
      }
      // Normalize by the open projection as well: gap must not cause a shift
      // at zero degrees. Side bezels can place the image off the lid's center.
      const center = .5 + (borderRight - borderLeft) / (2 * width);
      projection = [
        openDistance / denominator, 0, 0,
        center * slope / denominator, (pivot * slope + cosine * openDistance) / denominator, slope / denominator,
        center * (denominator - openDistance) / denominator,
        pivot * (denominator - cosine * openDistance) / denominator, 1,
      ];
    }
  }
  projection = projection.map(value => value === 0 ? 0 : value);
  if (![...projection, ...depth].every(Number.isFinite)) throw new RangeError('Desktop optics exceeds numeric limits');
  return {projection, depth, frontFacing};
}
