import Foundation
import IOKit.hid

final class LidSensor {
    var onSample: ((Double?,Double) -> Void)?
    private let deliveryLock=NSLock()
    private var pending:(Double?,Double)?
    private var deliveryScheduled=false
    var onPrecision: ((Bool) -> Void)?
    private var fineSupported=false,reads=0,mismatches=0
    private let queue = DispatchQueue(label: "com.zksaga.foldlight.lid", qos: .userInteractive)
    private var timer: DispatchSourceTimer?
    private var manager: IOHIDManager?
    private var sensor: IOHIDDevice?
    private var nextDiscovery: TimeInterval = 0
    private var lastValue: Double?
    private var failures = 0

    func start() {
        queue.async { [weak self] in
            guard let self, self.timer == nil else { return }
            let manager = IOHIDManagerCreate(kCFAllocatorDefault, 0)
            IOHIDManagerSetDeviceMatching(manager, [kIOHIDVendorIDKey:0x05ac,
                kIOHIDPrimaryUsagePageKey:0x20,kIOHIDPrimaryUsageKey:0x8a] as CFDictionary)
            self.manager = manager
            let timer = DispatchSource.makeTimerSource(queue:self.queue)
            timer.schedule(deadline:.now(),repeating:1.0/120,leeway:.milliseconds(1))
            timer.setEventHandler { [weak self] in self?.sample() }
            self.timer = timer; timer.resume()
        }
    }
    func stop() {
        queue.async { [weak self] in
            guard let self else { return }; self.timer?.cancel();self.timer=nil
            if let sensor=self.sensor { IOHIDDeviceClose(sensor,0) }
            self.sensor=nil;self.manager=nil;self.lastValue=nil;self.nextDiscovery=0
        }
    }
    private func report(_ device:IOHIDDevice,id:Int)->[UInt8]? {
        var bytes=[UInt8](repeating:0,count:8),length=8
        guard IOHIDDeviceGetReport(device,kIOHIDReportTypeFeature,id,&bytes,&length)==kIOReturnSuccess else {return nil}
        return Array(bytes.prefix(length))
    }
    private func setPrecision(_ fine:Bool) {
        fineSupported=fine
        DispatchQueue.main.async { [weak self] in self?.onPrecision?(fine) }
    }
    private func discoverPrecision(_ device:IOHIDDevice,coarse:Double) {
        // Vendor report 7 is used only when its descriptor advertises hundredth-degree
        // units and its value agrees with the standard integer-angle report.
        let described=(IOHIDDeviceCopyMatchingElements(device,nil,0) as? [IOHIDElement] ?? []).contains {
            IOHIDElementGetUsagePage($0)==0x20 && IOHIDElementGetUsage($0)==0x545 &&
            IOHIDElementGetReportID($0)==7 && IOHIDElementGetLogicalMax($0)==36000 &&
            IOHIDElementGetUnitExponent($0)==14
        }
        let fine=described ? report(device,id:7).flatMap(LidReport.fine):nil
        mismatches=0
        setPrecision(fine.map{LidReport.agrees(fine:$0,coarse:coarse)} ?? false)
    }
    private func read(_ device: IOHIDDevice) -> Double? {
        reads+=1
        if fineSupported,let fine=report(device,id:7).flatMap(LidReport.fine) {
            // Periodically cross-check, including after unexpected sensor format changes.
            if reads%60 != 0 { return fine }
            if let coarse=report(device,id:1).flatMap(LidReport.coarse),LidReport.agrees(fine:fine,coarse:coarse) { mismatches=0;return fine }
            // The two reports can straddle a firmware update during motion.
            // Require repeated disagreement before abandoning validated fine units.
            mismatches+=1
            if mismatches<3 {return fine}
        }
        if fineSupported { setPrecision(false) }
        return report(device,id:1).flatMap(LidReport.coarse)
    }
    private func emit(_ value: Double?) {
        guard value != lastValue || value == nil else{return}
        lastValue=value
        let time=ProcessInfo.processInfo.systemUptime
        // Keep the hardware timestamp; UI work must not queue stale angle events.
        deliveryLock.lock();pending=(value,time)
        let schedule = !deliveryScheduled;deliveryScheduled=true;deliveryLock.unlock()
        guard schedule else{return}
        DispatchQueue.main.async { [weak self] in
            guard let self else{return}
            self.deliveryLock.lock();let sample=self.pending;self.pending=nil
            self.deliveryScheduled=false;self.deliveryLock.unlock()
            if let sample {self.onSample?(sample.0,sample.1)}
        }
    }
    private func sample() {
        let now=ProcessInfo.processInfo.systemUptime
        if sensor == nil {
            guard now>=nextDiscovery, let manager else { return }
            nextDiscovery=now+2
            for device in IOHIDManagerCopyDevices(manager) as? Set<IOHIDDevice> ?? [] {
                guard IOHIDDeviceOpen(device,0)==kIOReturnSuccess else { continue }
                if let coarse=report(device,id:1).flatMap(LidReport.coarse) { sensor=device;failures=0;reads=0;discoverPrecision(device,coarse:coarse);break }
                IOHIDDeviceClose(device,0)
            }
            if sensor == nil { emit(nil);return }
        }
        guard let sensor else { return }
        if let value=read(sensor) { failures=0;emit(value) }
        else {
            failures+=1
            if failures>=5 {
                IOHIDDeviceClose(sensor,0);self.sensor=nil;emit(nil)
            }
        }
    }
}
