import AppKit

@MainActor final class ProbeView: NSView {
    var frameCount=0
    override func draw(_ dirtyRect:NSRect) {
        frameCount+=1
        NSColor(calibratedHue:Double(frameCount%240)/240,saturation:0.6,brightness:0.75,alpha:1).setFill();bounds.fill()
        let text="Foldlight motion check · \(frameCount)"
        (text as NSString).draw(at:NSPoint(x:25,y:120),withAttributes:[.font:NSFont.systemFont(ofSize:20),.foregroundColor:NSColor.white])
    }
}
@main @MainActor enum ProbeMain {
    static func main() {
        setbuf(stdout,nil)
        let app=NSApplication.shared;app.setActivationPolicy(.accessory)
        let frame=NSScreen.main!.visibleFrame
        let window=NSWindow(contentRect:NSRect(x:frame.minX+40,y:frame.minY+60,width:520,height:280),styleMask:[.titled],backing:.buffered,defer:false)
        window.title="折光 · 临时动画测试";window.isReleasedWhenClosed=false
        let view=ProbeView(frame:NSRect(x:0,y:0,width:520,height:280));window.contentView=view
        window.orderFrontRegardless()
        let animation=Timer(timeInterval:1/120,repeats:true) { _ in MainActor.assumeIsolated { view.needsDisplay=true } }
        RunLoop.main.add(animation,forMode:.common)
        var previous=0,last=ProcessInfo.processInfo.systemUptime
        let statistics=Timer(timeInterval:1,repeats:true) { _ in
            MainActor.assumeIsolated {
                let now=ProcessInfo.processInfo.systemUptime
                print("time=\(now) visible=\(window.occlusionState.contains(.visible)) drawFPS=\(Double(view.frameCount-previous)/(now-last))")
                previous=view.frameCount;last=now
            }
        }
        RunLoop.main.add(statistics,forMode:.common)
        DispatchQueue.main.asyncAfter(deadline:.now()+45) { NSApp.terminate(nil) }
        app.run()
    }
}
