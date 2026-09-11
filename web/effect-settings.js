export const effectDefaults=Object.freeze({blur:1,compression:.6,darkStart:80,lightStart:90});
const clamp=x=>Math.max(0,Math.min(1,x));
const smooth=x=>{x=clamp(x);return x*x*(3-2*x)};
// Map 0–90 degree phone tilt to 0–180 degree fold travel, with the same
// separate opening/closing brightness envelopes as the Android renderer.
export function nextBrightness(previous,angle){
 const physical=Math.min(180,Math.abs(angle)*2);
 const opening=1-smooth((physical-effectDefaults.darkStart)/(180-effectDefaults.darkStart));
 const closing=1-smooth((physical-40)/(effectDefaults.lightStart-40));
 return Number.isFinite(previous)?Math.max(closing,Math.min(opening,previous)):opening;
}
