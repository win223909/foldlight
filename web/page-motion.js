import {centeredFoldScroll} from './presentation.js';

export function installPageMotion({onProgress}) {
  const preference = matchMedia('(prefers-reduced-motion: reduce)');
  const root = document.documentElement;
  const hero = document.querySelector('.hero-scroll');
  const anchor = document.querySelector('.experience-anchor');
  const experience = document.querySelector('.experience');
  const device = document.querySelector('.device-motion');
  let frame = 0, lastScroll = null, lastMode = null;
  const reveal = new IntersectionObserver(entries => {
    for (const entry of entries) {
      if (entry.isIntersecting) {
        entry.target.classList.add('is-visible');
        reveal.unobserve(entry.target);
      }
    }
  }, {threshold: 0.08});
  for (const node of document.querySelectorAll('.reveal')) reveal.observe(node);
  root.classList.add('motion-ready');
  function update() {
    frame = 0;
    if (document.hidden) return;
    const mode = root.dataset.view, y = scrollY;
    const scrollChanged = lastScroll === null || Math.abs(y-lastScroll) > .5;
    const modeChanged = mode !== lastMode;
    lastScroll = y; lastMode = mode;
    if (preference.matches || document.body.classList.contains('immersed')) {
      root.style.setProperty('--device-lift', '0px');
      return;
    }
    const navHeight = document.querySelector('.topbar').offsetHeight;
    let progress = 0;
    if (mode === 'desktop') {
      const anchorTop = anchor.getBoundingClientRect().top + y;
      const heroTop = hero.getBoundingClientRect().top + y;
      const travel = Math.max(650, innerHeight * 1.06);
      const fold = centeredFoldScroll({scrollY:y,anchorTop,deviceHeight:device.offsetHeight,
        viewportHeight:innerHeight,navHeight,travel});
      root.style.setProperty('--mac-pin-top', `${fold.pinTop}px`);
      hero.style.height = `${anchorTop-heroTop+travel+experience.offsetHeight+Math.max(110,innerHeight*.16)}px`;
      progress = fold.progress;
      // Keep an inspectable boundary for layout and motion regression checks.
      hero.dataset.foldStart = String(fold.start);
      hero.dataset.foldTravel = String(travel);
    } else {
      hero.style.removeProperty('height');
      progress = Math.max(0,Math.min(1,(navHeight-hero.getBoundingClientRect().top)/Math.max(1,hero.offsetHeight*.7)));
    }
    root.style.setProperty('--device-lift', `${mode === 'desktop' ? 0 : -progress*22}px`);
    // Resizing, asset loads and language changes must not resume a paused drag.
    if (scrollChanged || modeChanged) onProgress(progress);
  }
  const schedule = () => { if (!frame) frame = requestAnimationFrame(update); };
  addEventListener('scroll', schedule, {passive:true});
  addEventListener('resize', schedule, {passive:true});
  preference.addEventListener?.('change', schedule);
  const layout = new ResizeObserver(schedule);
  layout.observe(device);
  layout.observe(document.querySelector('.intro'));
  document.addEventListener('visibilitychange', () => {
    if (document.hidden) {cancelAnimationFrame(frame);frame=0;}
    else schedule();
  });
  schedule();
}
