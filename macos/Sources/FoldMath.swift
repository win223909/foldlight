import Foundation

enum FoldMath {
    static func angle(raw: Double, reference: Double) -> Double {
        guard raw.isFinite, reference.isFinite else { return 0 }
        return min(0, max(-120, raw - reference))
    }
    static func smooth(_ current: Double, toward target: Double, dt: Double, response:Double=0.018) -> Double {
        let bounded = min(0, max(-120, target))
        let value = current + (bounded-current) * (1-exp(-max(0,min(dt,0.1))/max(0.001,response)))
        return bounded == 0 && abs(value) < 0.001 ? 0 : value
    }
    static func overlayOpacity(angle:Double)->Double {
        guard angle.isFinite else{return 0}
        let progress=min(1,max(0,(abs(angle)-0.35)/2.15))
        return progress*progress*(3-2*progress)
    }
    static func pixelSize(width: Double, height: Double, scale: Double) -> (Int, Int) {
        let density = min(scale, sqrt(6_000_000 / max(1,width*height)))
        return (max(2,Int(width*density)),max(2,Int(height*density)))
    }
}

/// HID feature reports on some Macs refresh at ~10 Hz even when polled at 120 Hz.
/// Interpolate timestamped measurements behind the input clock instead of chasing
/// each new measurement immediately and standing still until the next report.
struct LidMotionTimeline {
    private struct Sample { let time:Double;let angle:Double }
    private var samples=[Sample]()
    let delay:Double = 0.115
    mutating func reset(angle:Double=0,at time:Double) { samples=[Sample(time:time,angle:angle)] }
    mutating func append(_ angle:Double,at time:Double) {
        guard angle.isFinite,time.isFinite else{return}
        let bounded=min(0,max(-120,angle))
        guard let last=samples.last else {reset(angle:bounded,at:time);return}
        guard time>last.time,bounded != last.angle else{return}
        // Unchanged reports are not new hardware measurements. After a stationary
        // interval, hold the prior angle until one hardware period before this one.
        if time-last.time>delay {samples.append(Sample(time:time-delay,angle:last.angle))}
        samples.append(Sample(time:time,angle:bounded))
        if samples.count>64 {samples.removeFirst(samples.count-64)}
    }
    func value(at time:Double)->Double {
        guard let first=samples.first else{return 0}
        let playback=time-delay
        if playback<=first.time {return first.angle}
        for (a,b) in zip(samples,samples.dropFirst()) where playback<=b.time {
            let fraction=min(1,max(0,(playback-a.time)/(b.time-a.time)))
            return a.angle+(b.angle-a.angle)*fraction
        }
        return samples.last!.angle
    }
}
