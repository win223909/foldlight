// Device presentation does not change the underlying sensor math.
export function clampPreviewAngle(value, mode) {
  const angle = Number(value);
  if (!Number.isFinite(angle)) return 0;
  return mode === 'desktop'
    ? Math.max(-120, Math.min(0, angle))
    : Math.max(-90, Math.min(90, angle));
}

export function scrollPreviewAngle(progress) {
  const p = Math.max(0, Math.min(1, Number(progress) || 0));
  return p === 0 ? 0 : -120 * p * p * (3 - 2 * p);
}

export function centeredFoldScroll({scrollY, anchorTop, deviceHeight, viewportHeight, navHeight, travel}) {
  const pinTop = Math.max(navHeight + 16, (viewportHeight - deviceHeight) / 2);
  const start = anchorTop - pinTop;
  const progress = Math.max(0, Math.min(1, (scrollY - start) / Math.max(1, travel)));
  return {pinTop, start, progress};
}
