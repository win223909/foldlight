import AppKit
import MetalKit
import CoreVideo

final class FrameMailbox {
    private let lock=NSLock()
    private var buffer: CVPixelBuffer?
    private(set) var received=0
    func put(_ value: CVPixelBuffer) { lock.lock();buffer=value;received+=1;lock.unlock() }
    func get() -> (pixel:CVPixelBuffer,revision:Int)? { lock.lock();defer{lock.unlock()};guard let buffer else{return nil};return(buffer,received) }
    func clear() { lock.lock();buffer=nil;lock.unlock() }
    func count() -> Int { lock.lock();defer{lock.unlock()};return received }
}

final class RenderStatistics {
    private let lock=NSLock()
    private var presented=0,completed=0,gpuTotal=0.0
    private var lastPresentation=0.0,intervals=[Double]()
    private var lastOpacity:Double?
    func presentedOpacity()->Double? {lock.lock();defer{lock.unlock()};return lastOpacity}
    func beginActiveInterval() {lock.lock();lastPresentation=0;lock.unlock()}
    func didPresent(at time:Double,opacity:Double) {
        lock.lock();defer{lock.unlock()}
        if lastPresentation>0, time>lastPresentation { intervals.append((time-lastPresentation)*1000) }
        if intervals.count>7200 {intervals.removeFirst(1200)}
        lastPresentation=time;lastOpacity=opacity;presented+=1
    }
    func timingReport()->[String:Double] {
        lock.lock();defer{lock.unlock()}
        let sorted=intervals.sorted();guard !sorted.isEmpty else{return [:]}
        return ["presentationP50MS":sorted[sorted.count/2],"presentationP95MS":sorted[min(sorted.count-1,Int(Double(sorted.count)*0.95))],
                "presentationMaxMS":sorted.last!,"gapsOver25MS":Double(sorted.filter{$0>25}.count)]
    }
    func didComplete(_ seconds: Double) { lock.lock();completed+=1;gpuTotal+=max(0,seconds);lock.unlock() }
    func snapshot()->(Int,Int,Double) { lock.lock();defer{lock.unlock()};return(presented,completed,gpuTotal) }
}

final class DesktopRenderer: NSObject, MTKViewDelegate {
    let device: MTLDevice
    let mailbox=FrameMailbox()
    let statistics=RenderStatistics()
    private(set) var target=0.0
    private var motion=LidMotionTimeline()
    private var bufferedMotion=false
    private var targetRevision=0
    private var hasPresentedRest=false
    private(set) var isEffectVisible=false
    var visibilityChanged:(()->Void)?
    func setTarget(_ value:Double,at time:Double,buffered:Bool) {
        if value != target || buffered != bufferedMotion {targetRevision+=1;hasPresentedRest=false}
        if buffered != bufferedMotion {motion.reset(angle:current,at:time-motion.delay)}
        bufferedMotion=buffered;target=value
        if buffered {motion.append(value,at:time)}
    }
    func recenterMotion() { targetRevision+=1;hasPresentedRest=false;current=0;target=0;motion.reset(at:CACurrentMediaTime()) }
    var blur=0.08
    var responseSeconds=0.018
    var settled: (() -> Void)?
    var failure: ((String) -> Void)?
    private(set) var renderedFrames=0
    private let commandQueue: MTLCommandQueue
    private let pipeline: MTLRenderPipelineState
    private let glassBlur: GlassBlur
    private var lastGlassFrame = -1
    private var textureCache: CVMetalTextureCache!
    private let inFlight=DispatchSemaphore(value:1)
    private var current=0.0,lastTime=CACurrentMediaTime()
    private weak var view: MTKView?

    init(device: MTLDevice) throws {
        self.device=device
        guard let queue=device.makeCommandQueue() else { throw NSError(domain:"Metal",code:1) }
        commandQueue=queue
        let shader=try String(contentsOf:Bundle.main.url(forResource:"Fold",withExtension:"metal")!,encoding:.utf8)
        let library=try device.makeLibrary(source:shader,options:nil)
        glassBlur=try GlassBlur(device:device,library:library)
        let desc=MTLRenderPipelineDescriptor()
        desc.vertexFunction=library.makeFunction(name:"foldVertex")
        desc.fragmentFunction=library.makeFunction(name:"foldFragment")
        desc.colorAttachments[0].pixelFormat = .bgra8Unorm
        pipeline=try device.makeRenderPipelineState(descriptor:desc)
        super.init()
        guard CVMetalTextureCacheCreate(kCFAllocatorDefault,nil,device,nil,&textureCache)==kCVReturnSuccess else {
            throw NSError(domain:"MetalTextureCache",code:1)
        }
    }
    func attach(_ view: MTKView) { self.view=view;view.delegate=self }
    func reset() { recenterMotion();lastTime=CACurrentMediaTime();mailbox.clear() }
    func resume() {
        guard target != 0 || !hasPresentedRest else{return}
        if view?.isPaused == true {statistics.beginActiveInterval();lastTime=CACurrentMediaTime();view?.isPaused=false}
    }
    func mtkView(_ view: MTKView, drawableSizeWillChange size: CGSize) {}
    func draw(in view: MTKView) {
        let now=CACurrentMediaTime()
        let animationTarget=bufferedMotion ? motion.value(at:now):target
        current=FoldMath.smooth(current,toward:animationTarget,dt:now-lastTime,response:responseSeconds);lastTime=now
        let resting=abs(current)<0.03 && abs(animationTarget)<0.03 && target==0
        if resting {current=0}
        let opacity=FoldMath.overlayOpacity(angle:current)
        let revision=targetRevision
        guard let frame=mailbox.get(),inFlight.wait(timeout:.now()) == .success else { return }
        let pixel=frame.pixel
        var wrapper: CVMetalTexture?
        let result=CVMetalTextureCacheCreateTextureFromImage(kCFAllocatorDefault,textureCache,pixel,nil,
            .bgra8Unorm,CVPixelBufferGetWidth(pixel),CVPixelBufferGetHeight(pixel),0,&wrapper)
        guard result==kCVReturnSuccess,let wrapper,let texture=CVMetalTextureGetTexture(wrapper),
              let pass=view.currentRenderPassDescriptor,let drawable=view.currentDrawable,
              let command=commandQueue.makeCommandBuffer() else {
            inFlight.signal();return
        }
        let glass:MTLTexture
        do {
            glass=try glassBlur.encode(texture,command:command,refresh:frame.revision != lastGlassFrame)
            lastGlassFrame=frame.revision
        }catch{inFlight.signal();failure?(error.localizedDescription);return}
        guard let encoder=command.makeRenderCommandEncoder(descriptor:pass) else {inFlight.signal();return}
        var uniforms=[SIMD4<Float>(Float(view.bounds.width),Float(view.bounds.height),
            Float(view.bounds.width/view.drawableSize.width),Float(view.bounds.height/view.drawableSize.height)),
            SIMD4<Float>(Float(current * .pi/180),Float(blur),Float(opacity),glassBlur.baseSigma)]
        encoder.setRenderPipelineState(pipeline)
        encoder.setFragmentTexture(texture,index:0)
        encoder.setFragmentTexture(glass,index:1)
        encoder.setFragmentBytes(&uniforms,length:MemoryLayout<SIMD4<Float>>.stride*2,index:0)
        encoder.drawPrimitives(type:.triangle,vertexStart:0,vertexCount:3);encoder.endEncoding()
        let statistics=self.statistics
        drawable.addPresentedHandler { [weak self] drawable in
            guard drawable.presentedTime>0 else{return}
            statistics.didPresent(at:drawable.presentedTime,opacity:opacity)
            if resting {
                // Do not pause on CPU submission: the last visible drawable might
                // still contain the fold. Wait until the transparent frame presents.
                DispatchQueue.main.async {
                    guard let self,self.targetRevision==revision,self.target==0 else{return}
                    self.hasPresentedRest=true;self.view?.isPaused=true;self.settled?()
                }
            }
        }
        command.present(drawable)
        let semaphore=inFlight
        command.addCompletedHandler { [weak self] command in
            // Retain the IOSurface-backed buffer and its texture until the GPU finishes.
            withExtendedLifetime((pixel,wrapper)) {}
            semaphore.signal()
            statistics.didComplete(command.gpuEndTime-command.gpuStartTime)
            if command.status == .error {
                DispatchQueue.main.async { self?.failure?(command.error?.localizedDescription ?? "Metal error") }
            }
        }
        command.commit();renderedFrames+=1
        let visible=opacity>0.001
        if visible != isEffectVisible {isEffectVisible=visible;visibilityChanged?()}
    }
}

final class TransparentMetalView: MTKView {
    override var isOpaque:Bool {false}
}

final class OverlayWindow: NSPanel {
    override var canBecomeKey: Bool { false }
    override var canBecomeMain: Bool { false }
    init(screen: NSScreen, renderer: DesktopRenderer, fps: Int) {
        super.init(contentRect:screen.frame,styleMask:[.borderless,.nonactivatingPanel],backing:.buffered,defer:false)
        isOpaque=false;backgroundColor = .clear;hasShadow=false;ignoresMouseEvents=true
        level=NSWindow.Level(rawValue:Int(CGWindowLevelForKey(.statusWindow))+1)
        collectionBehavior=[.canJoinAllSpaces,.fullScreenAuxiliary,.stationary,.ignoresCycle]
        hidesOnDeactivate=false;isReleasedWhenClosed=false;animationBehavior = .none
        let view=TransparentMetalView(frame:NSRect(origin:.zero,size:screen.frame.size),device:renderer.device)
        view.colorPixelFormat = .bgra8Unorm;view.framebufferOnly=true;view.clearColor=MTLClearColorMake(0,0,0,0)
        view.preferredFramesPerSecond=fps;view.enableSetNeedsDisplay=false;view.isPaused=true
        if let layer=view.layer as? CAMetalLayer {
            layer.isOpaque=false
            layer.colorspace=CGColorSpace(name:CGColorSpace.sRGB)
            layer.maximumDrawableCount=2
            layer.presentsWithTransaction=false
        }
        contentView=view;renderer.attach(view)
    }
    var metalView: MTKView { contentView as! MTKView }
}
