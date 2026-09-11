import CoreImage
import MetalKit
import OSLog
import QuartzCore
import SwiftUI

/// A static photo texture is uploaded once; device motion only changes small frame uniforms.
struct FoldMetalView: UIViewRepresentable {
    let image: UIImage
    let motion: FoldMotionModel
    let isActive: Bool
    var manualDegrees: Double? = nil
    var onFailure: (() -> Void)? = nil

    func makeCoordinator() -> Coordinator { Coordinator(motion: motion) }

    func makeUIView(context: Context) -> FoldCanvasView {
        let view = FoldCanvasView(frame: .zero, device: context.coordinator.device)
        view.backgroundColor = .black
        view.isOpaque = true
        view.clearColor = MTLClearColor(red: 0, green: 0, blue: 0, alpha: 1)
        view.colorPixelFormat = .bgra8Unorm_srgb
        (view.layer as? CAMetalLayer)?.colorspace = CGColorSpace(name: CGColorSpace.sRGB)
        view.framebufferOnly = true
        view.sampleCount = 1
        view.isPaused = true
        view.enableSetNeedsDisplay = false
        view.autoResizeDrawable = true
        view.isUserInteractionEnabled = false
        context.coordinator.attach(view)
        return view
    }

    func updateUIView(_ uiView: FoldCanvasView, context: Context) {
        context.coordinator.configure(
            image: image, motion: motion, isActive: isActive, manualDegrees: manualDegrees, onFailure: onFailure
        )
    }

    static func dismantleUIView(_ uiView: FoldCanvasView, coordinator: Coordinator) {
        coordinator.tearDown()
    }

    @MainActor
    final class Coordinator: NSObject, MTKViewDelegate {
        let device = MTLCreateSystemDefaultDevice()
        private weak var view: FoldCanvasView?
        private var motion: FoldMotionModel
        private var commandQueue: (any MTLCommandQueue)?
        private var pipeline: (any MTLRenderPipelineState)?
        private var texture: (any MTLTexture)?
        private var glassTexture: (any MTLTexture)?
        private var glassSigma: Float = 4
        private var sourceImage: UIImage?
        private var uploadTask: Task<Void, Never>?
        private var uploadRevision = 0
        private var isActive = false
        private var manualDegrees: Double?
        private var onFailure: (() -> Void)?
        private var hasFailed = false
        private var hasReportedFailure = false
        private let parameters = FoldParameters()
        private let inFlight = DispatchSemaphore(value: 2)
        private let statistics = FoldGPUStatistics()
        private let recordsStatistics = ProcessInfo.processInfo.arguments.contains("-metalStats")
        private static let logger = Logger(subsystem: "FoldMetal", category: "Renderer")

        init(motion: FoldMotionModel) {
            self.motion = motion
            super.init()
            if let device {
                commandQueue = device.makeCommandQueue()
                do {
                    guard let library = device.makeDefaultLibrary(),
                          let vertex = library.makeFunction(name: "foldTextureVertex"),
                          let fragment = library.makeFunction(name: "foldTextureFragment")
                    else { throw FoldTextureError.unavailable }
                    let descriptor = MTLRenderPipelineDescriptor()
                    descriptor.label = "Fold Gaussian glass pipeline"
                    descriptor.vertexFunction = vertex
                    descriptor.fragmentFunction = fragment
                    descriptor.colorAttachments[0].pixelFormat = .bgra8Unorm_srgb
                    pipeline = try device.makeRenderPipelineState(descriptor: descriptor)
                } catch {
                    Self.logger.error("Metal pipeline unavailable: \(error.localizedDescription, privacy: .public)")
                }
            }
            hasFailed = device == nil || commandQueue == nil || pipeline == nil
            NotificationCenter.default.addObserver(
                self, selector: #selector(resumeDrawing),
                name: UIApplication.didBecomeActiveNotification, object: nil
            )
            NotificationCenter.default.addObserver(
                self, selector: #selector(suspendDrawing),
                name: UIApplication.willResignActiveNotification, object: nil
            )
        }

        deinit { NotificationCenter.default.removeObserver(self) }

        func attach(_ view: FoldCanvasView) {
            self.view = view
            view.delegate = self
            view.geometryChanged = { [weak self] in self?.updatePlayback() }
        }

        func configure(image: UIImage, motion: FoldMotionModel, isActive: Bool, manualDegrees: Double?,
                       onFailure: (() -> Void)?) {
            self.motion = motion
            self.isActive = isActive
            self.manualDegrees = manualDegrees
            self.onFailure = onFailure
            guard !hasFailed else {
                reportFailure()
                return
            }
            if sourceImage !== image {
                sourceImage = image
                upload(image)
            }
            updatePlayback()
        }

        private func upload(_ image: UIImage) {
            uploadTask?.cancel()
            uploadRevision += 1
            let revision = uploadRevision
            guard let device, let source = image.cgImage else {
                fail("The selected image has no usable bitmap.")
                return
            }
            let orientation = image.imageOrientation
            uploadTask = Task { [weak self] in
                let result = await Task.detached(priority: .userInitiated) {
                    Result { try FoldTextureFactory.makeTexture(source: source, orientation: orientation, device: device) }
                }.value
                guard let self, !Task.isCancelled, self.uploadRevision == revision else { return }
                switch result {
                case .success(let textures):
                    self.texture = textures.photo
                    self.glassTexture = textures.glass
                    self.glassSigma = textures.sigma
                    self.updatePlayback()
                case .failure(let error):
                    self.fail(error.localizedDescription)
                }
            }
        }

        private func fail(_ reason: String) {
            Self.logger.error("Metal rendering unavailable: \(reason, privacy: .public)")
            hasFailed = true
            texture = nil
            glassTexture = nil
            view?.enableSetNeedsDisplay = false
            view?.isPaused = true
            reportFailure()
        }

        private func reportFailure() {
            guard !hasReportedFailure, let onFailure else { return }
            hasReportedFailure = true
            // configure is called during a SwiftUI update; publish fallback state next turn.
            Task { @MainActor in onFailure() }
        }

        private func updatePlayback() {
            guard let view else { return }
            let canDraw = isActive && view.window != nil
                && UIApplication.shared.applicationState == .active
                && !hasFailed && pipeline != nil && texture != nil
            view.preferredFramesPerSecond = min(view.window?.screen.maximumFramesPerSecond ?? 60, 120)
            let eventDriven = manualDegrees != nil
            view.enableSetNeedsDisplay = canDraw && eventDriven
            view.isPaused = !canDraw || eventDriven
            if canDraw && eventDriven { view.setNeedsDisplay() }
        }

        @objc private func resumeDrawing() { updatePlayback() }

        @objc private func suspendDrawing() {
            view?.enableSetNeedsDisplay = false
            view?.isPaused = true
        }

        func tearDown() {
            isActive = false
            uploadRevision += 1
            uploadTask?.cancel()
            uploadTask = nil
            view?.isPaused = true
            view?.enableSetNeedsDisplay = false
            view?.delegate = nil
            view?.geometryChanged = nil
            view?.releaseDrawables()
            texture = nil
            glassTexture = nil
            sourceImage = nil
            onFailure = nil
            view = nil
        }

        func mtkView(_ view: MTKView, drawableSizeWillChange size: CGSize) {
            if manualDegrees != nil, isActive { view.setNeedsDisplay() }
        }

        func draw(in view: MTKView) {
            guard isActive, view.window != nil, UIApplication.shared.applicationState == .active,
                  let pipeline, let texture, let glassTexture, let commandQueue,
                  view.bounds.width > 0, view.bounds.height > 0,
                  view.drawableSize.width > 0, view.drawableSize.height > 0,
                  inFlight.wait(timeout: .now()) == .success
            else { return }

            var submitted = false
            defer { if !submitted { inFlight.signal() } }
            guard let descriptor = view.currentRenderPassDescriptor, let drawable = view.currentDrawable,
                  let command = commandQueue.makeCommandBuffer(),
                  let encoder = command.makeRenderCommandEncoder(descriptor: descriptor)
            else { return }

            // Reading the observable model here does not register a SwiftUI body dependency.
            let rawAngle = manualDegrees.map { $0 * .pi / 180 } ?? motion.tiltAngle
            let limit = FoldMotionModel.maximumTiltDegrees * .pi / 180
            let angle = rawAngle.isFinite ? min(max(rawAngle, -limit), limit) : 0
            let size = SIMD2(Float(view.bounds.width), Float(view.bounds.height))
            let textureSize = SIMD2(Float(texture.width), Float(texture.height))
            let fillScale = max(size.x / textureSize.x, size.y / textureSize.y)
            let filledSize = textureSize * fillScale
            let offset = (filledSize - size) * 0.5 / filledSize
            var uniforms = FoldTextureUniforms(
                viewport: SIMD4(size.x, size.y,
                                size.x / Float(view.drawableSize.width), size.y / Float(view.drawableSize.height)),
                fold: SIMD4(Float(sin(abs(angle))), Float(cos(abs(angle))),
                            angle > 0 ? size.x : 0, Float(parameters.eyeDistancePoints)),
                material: SIMD4(Float(parameters.blurSpread), Float(parameters.darkening),
                                1 / fillScale, glassSigma),
                mapping: SIMD4(1 / filledSize.x, 1 / filledSize.y, offset.x, offset.y)
            )
            encoder.label = "Cached Gaussian glass photo"
            encoder.setRenderPipelineState(pipeline)
            encoder.setFragmentTexture(texture, index: 0)
            encoder.setFragmentTexture(glassTexture, index: 1)
            encoder.setFragmentBytes(&uniforms, length: MemoryLayout<FoldTextureUniforms>.stride, index: 0)
            encoder.drawPrimitives(type: .triangle, vertexStart: 0, vertexCount: 3)
            encoder.endEncoding()
            let semaphore = inFlight
            let statistics = statistics
            let recordsStatistics = recordsStatistics
            command.addCompletedHandler { [weak self] buffer in
                semaphore.signal()
                if recordsStatistics { statistics.record(buffer) }
                if buffer.status == .error {
                    let reason = buffer.error?.localizedDescription ?? "The GPU could not complete the frame."
                    Task { @MainActor [weak self] in self?.fail(reason) }
                }
            }
            command.present(drawable)
            submitted = true
            command.commit()
        }
    }
}

/// Match drawable resolution to at most 2x points, independently of a 3x screen's native scale.
final class FoldCanvasView: MTKView {
    var geometryChanged: (() -> Void)?

    override func didMoveToWindow() {
        super.didMoveToWindow()
        contentScaleFactor = min(window?.screen.scale ?? traitCollection.displayScale, 2)
        geometryChanged?()
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        let scale = min(window?.screen.scale ?? traitCollection.displayScale, 2)
        let targetSize = CGSize(width: max(1, bounds.width * scale), height: max(1, bounds.height * scale))
        if drawableSize != targetSize { drawableSize = targetSize }
        geometryChanged?()
    }
}

private struct FoldTextureUniforms {
    var viewport: SIMD4<Float>
    var fold: SIMD4<Float>
    var material: SIMD4<Float>
    var mapping: SIMD4<Float>
}

nonisolated private enum FoldTextureError: Error { case unavailable, invalidImage }

nonisolated struct FoldPhotoTextures: @unchecked Sendable {
    let photo: any MTLTexture
    let glass: any MTLTexture
    let sigma: Float
}

nonisolated enum FoldTextureFactory {
    static func makeTexture(source: CGImage, orientation: UIImage.Orientation, device: any MTLDevice) throws -> FoldPhotoTextures {
        guard let colorSpace = CGColorSpace(name: CGColorSpace.sRGB) else { throw FoldTextureError.invalidImage }
        var normalized = source
        if orientation != .up {
            let exif: Int32
            switch orientation {
            case .up: exif = 1
            case .upMirrored: exif = 2
            case .down: exif = 3
            case .downMirrored: exif = 4
            case .leftMirrored: exif = 5
            case .right: exif = 6
            case .rightMirrored: exif = 7
            case .left: exif = 8
            @unknown default: exif = 1
            }
            let oriented = CIImage(cgImage: source).oriented(forExifOrientation: exif)
            guard let image = CIContext().createCGImage(oriented, from: oriented.extent,
                                                       format: .RGBA8, colorSpace: colorSpace)
            else { throw FoldTextureError.invalidImage }
            normalized = image
        }
        // SRGB texture options tag transfer functions; they do not convert P3/CMYK primaries.
        // Draw once into a known 8-bit sRGB bitmap and flatten any transparency over black.
        guard let context = CGContext(
            data: nil, width: normalized.width, height: normalized.height,
            bitsPerComponent: 8, bytesPerRow: normalized.width * 4, space: colorSpace,
            bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue | CGBitmapInfo.byteOrder32Big.rawValue
        ) else { throw FoldTextureError.invalidImage }
        let rect = CGRect(x: 0, y: 0, width: CGFloat(normalized.width), height: CGFloat(normalized.height))
        context.setFillColor(CGColor(gray: 0, alpha: 1))
        context.fill(rect)
        context.draw(normalized, in: rect)
        guard let bytes = context.data else { throw FoldTextureError.invalidImage }
        // The CGImage texture-loader path can return rgba8Unorm even with SRGB: true.
        // Explicitly choose the format so encoded sRGB bytes are decoded when sampled,
        // before the drawable's sRGB format performs the one required output encoding.
        let stagingDescriptor = MTLTextureDescriptor.texture2DDescriptor(
            pixelFormat: .rgba8Unorm_srgb, width: normalized.width, height: normalized.height, mipmapped: false
        )
        stagingDescriptor.storageMode = .shared
        stagingDescriptor.usage = .shaderRead
        let textureDescriptor = MTLTextureDescriptor.texture2DDescriptor(
            pixelFormat: .rgba8Unorm_srgb, width: normalized.width, height: normalized.height, mipmapped: true
        )
        textureDescriptor.storageMode = .private
        textureDescriptor.usage = .shaderRead
        guard let staging = device.makeTexture(descriptor: stagingDescriptor),
              let texture = device.makeTexture(descriptor: textureDescriptor),
              let queue = device.makeCommandQueue(), let command = queue.makeCommandBuffer(),
              let blit = command.makeBlitCommandEncoder()
        else { throw FoldTextureError.unavailable }
        let size = MTLSize(width: normalized.width, height: normalized.height, depth: 1)
        staging.replace(region: MTLRegion(origin: MTLOrigin(x: 0, y: 0, z: 0), size: size),
                        mipmapLevel: 0, withBytes: bytes, bytesPerRow: context.bytesPerRow)
        blit.copy(from: staging, sourceSlice: 0, sourceLevel: 0, sourceOrigin: MTLOrigin(x: 0, y: 0, z: 0),
                  sourceSize: size, to: texture, destinationSlice: 0, destinationLevel: 0,
                  destinationOrigin: MTLOrigin(x: 0, y: 0, z: 0))
        blit.generateMipmaps(for: texture)
        blit.endEncoding()
        command.commit()
        // This factory runs in a detached task, never on the UI or render thread.
        command.waitUntilCompleted()
        guard command.status == .completed else {
            if let error = command.error { throw error }
            throw FoldTextureError.unavailable
        }
        guard let library=device.makeDefaultLibrary(),let glassCommand=queue.makeCommandBuffer() else {throw FoldTextureError.unavailable}
        let blur=try FoldGlassBlur(device:device,library:library)
        let glass=try blur.encode(texture,command:glassCommand,refresh:true)
        glassCommand.commit();glassCommand.waitUntilCompleted()
        guard glassCommand.status == .completed else {throw glassCommand.error ?? FoldTextureError.unavailable}
        return FoldPhotoTextures(photo:texture,glass:glass,sigma:blur.baseSigma)
    }
}

nonisolated private final class FoldGPUStatistics: @unchecked Sendable {
    private let lock = NSLock()
    private var frames = 0
    private var totalMilliseconds = 0.0
    private var maximumMilliseconds = 0.0
    private let logger = Logger(subsystem: "FoldMetal", category: "GPU")

    func record(_ command: any MTLCommandBuffer) {
        guard command.status == .completed, command.gpuEndTime >= command.gpuStartTime,
              command.gpuStartTime > 0 else { return }
        let duration = (command.gpuEndTime - command.gpuStartTime) * 1_000
        lock.withLock {
            frames += 1
            totalMilliseconds += duration
            maximumMilliseconds = max(maximumMilliseconds, duration)
            if frames == 120 {
                let average = totalMilliseconds / Double(frames)
                logger.info("120 frames GPU average \(average, privacy: .public) ms, max \(self.maximumMilliseconds, privacy: .public) ms")
                print("FoldMetal GPU frames=120 avg_ms=\(average) max_ms=\(maximumMilliseconds)")
                frames = 0
                totalMilliseconds = 0
                maximumMilliseconds = 0
            }
        }
    }
}
