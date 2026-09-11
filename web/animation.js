// Time-based damping keeps motion response consistent on 60 Hz and 120 Hz displays.
export function smoothAngle(current, target, elapsedMs, timeConstantMs = 28) {
 return current + (target - current) * -Math.expm1(-Math.max(0, elapsedMs) / timeConstantMs);
}
