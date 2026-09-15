// Select the initial layout before the page paints; compatible with script-src 'self'.
document.documentElement.dataset.view = /iPhone|iPad|iPod|Android/i.test(navigator.userAgent)
  || (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1)
  ? 'phone' : 'desktop';

// Start only the current device/language image before the module graph loads.
// URLs are supplied by the release build, so preload and canvas use one cache key.
let initialLanguage = navigator.language.startsWith('zh') ? 'zh' : 'en';
try {
  const saved = localStorage.getItem('foldlight-language');
  if (saved === 'zh' || saved === 'en') initialLanguage = saved;
} catch {}
const imageURL = document.currentScript?.getAttribute(
  `data-${document.documentElement.dataset.view}-${initialLanguage}`);
if (imageURL) {
  const preload = document.createElement('link');
  preload.rel = 'preload';
  preload.as = 'image';
  preload.fetchPriority = 'high';
  preload.href = imageURL;
  document.head.append(preload);
}
