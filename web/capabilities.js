export function deviceKind({platform='',userAgent='',maxTouchPoints=0}={}) {
 if (/iPhone|iPad|iPod|Android/i.test(userAgent)||(platform==='MacIntel'&&maxTouchPoints>1)) return 'phone';
 return 'desktop';
}
export function isMac({platform='',userAgent='',maxTouchPoints=0}={}) {
 return /Mac/.test(platform+' '+userAgent)&&maxTouchPoints<2&&!/iPhone|iPad|iPod/.test(userAgent);
}
export function motionValue(event,rotation=0) {
 const value=rotation===90?event.beta:rotation===270||rotation===-90?-event.beta:event.gamma;
 return typeof value==='number'&&Number.isFinite(value)?value:null;
}
