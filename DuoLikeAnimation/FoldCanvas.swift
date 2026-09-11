import Metal
import SwiftUI

/// The scene is static. Upload it once, then let Metal sample the current attitude at draw time.
struct FoldCanvas: View {
    let image: UIImage?
    let motion: FoldMotionModel
    let isActive: Bool
    @State private var demoSnapshot: UIImage?
    @State private var metalFailed = false
    private static let hasMetal = MTLCreateSystemDefaultDevice() != nil

    var body: some View {
        GeometryReader { proxy in
            Group {
                if Self.hasMetal, !metalFailed, let texture = image ?? demoSnapshot {
                    FoldMetalView(image: texture, motion: motion, isActive: isActive,
                                  manualDegrees: motion.usesManualTilt ? motion.manualDegrees : nil,
                                  onFailure: { metalFailed = true })
                } else {
                    fallback(size: proxy.size)
                        .clipped()
                        .foldEffect(angle: motion.tiltAngle)
                }
            }
            .frame(width: proxy.size.width, height: proxy.size.height)
            .clipped()
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(image == nil ? "折光内置示例" : "你的照片")
            .accessibilityIdentifier(image == nil ? "demoContent" : "photoContent")
            .task(id: proxy.size) {
                guard proxy.size.width > 0, proxy.size.height > 0 else { return }
                let renderer = ImageRenderer(content: DemoContentView()
                    .frame(width: proxy.size.width, height: proxy.size.height)
                    .environment(\.colorScheme, .dark))
                renderer.scale = 2
                renderer.isOpaque = true
                demoSnapshot = renderer.uiImage
            }
        }
    }

    @ViewBuilder
    private func fallback(size: CGSize) -> some View {
        if let image {
            Image(uiImage: image).resizable().scaledToFill()
                .frame(width: size.width, height: size.height)
        } else {
            DemoContentView().frame(width: size.width, height: size.height)
        }
    }
}
