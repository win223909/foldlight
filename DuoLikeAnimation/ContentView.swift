import PhotosUI
import SwiftUI

struct ContentView: View {
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var motion = FoldMotionModel()
    @State private var photos = PhotoStore(storageURL: ProcessInfo.processInfo.arguments.contains("-uiTesting")
        ? FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString).appendingPathComponent("photo.jpg") : nil)
    @State private var selection: PhotosPickerItem?
    @State private var showsPicker = false
    @State private var showsSettings = false
    @State private var showsControls = true
    @State private var samplePhoto: UIImage? = ProcessInfo.processInfo.arguments.contains("-samplePhoto")
        ? PreviewPhoto.make() : nil

    private var displayedPhoto: UIImage? { samplePhoto ?? photos.image }

    var body: some View {
        FoldCanvas(image: displayedPhoto, motion: motion,
                   isActive: scenePhase == .active && !showsPicker && !showsSettings)
            .contentShape(Rectangle())
            .onTapGesture {
                withAnimation(.easeInOut(duration: 0.2)) { showsControls.toggle() }
            }
            .accessibilityAction(named: "显示操作按钮") { showsControls = true }
        .ignoresSafeArea()
        .background(.black)
        .overlay(alignment: .bottom) {
            if showsControls { toolbar.transition(.opacity) }
        }
        .overlay {
            if photos.isLoading {
                ProgressView("正在准备照片…")
                    .padding(24)
                    .background(.regularMaterial, in: .rect(cornerRadius: 22))
            }
        }
        .photosPicker(isPresented: $showsPicker, selection: $selection, matching: .images)
        .sheet(isPresented: $showsSettings) { settings }
        .alert("照片未能载入", isPresented: Binding(
            get: { photos.errorMessage != nil },
            set: { if !$0 { photos.errorMessage = nil } }
        )) {
            Button("好", role: .cancel) { photos.errorMessage = nil }
        } message: {
            Text(photos.errorMessage ?? "请换一张照片再试。")
        }
        .task(id: selection) {
            guard let selection else { return }
            await photos.load(selection)
            if photos.errorMessage == nil, !Task.isCancelled { samplePhoto = nil }
            updateMotion()
        }
        .onAppear {
            if reduceMotion { motion.usesManualTilt = true }
            updateMotion()
        }
        .onDisappear { motion.stop() }
        .onChange(of: scenePhase) { _, _ in updateMotion() }
        .onChange(of: showsPicker) { _, _ in updateMotion() }
        .onChange(of: showsSettings) { _, _ in updateMotion() }
        .onChange(of: reduceMotion) { _, reduced in
            if reduced { motion.usesManualTilt = true }
        }
        .preferredColorScheme(.dark)
    }

    private var toolbar: some View {
        VStack(spacing: 12) {
            Text(motion.usesManualTilt ? "手动预览 · 在设置中调整角度" : "持正手机校准，再左右转动")
                .font(.caption.weight(.medium))
                .foregroundStyle(.white.opacity(0.85))
                .padding(.horizontal, 14)
                .padding(.vertical, 7)
                .background(.black.opacity(0.4), in: .capsule)
            HStack(spacing: 0) {
                Button {
                    selection = nil
                    showsPicker = true
                } label: {
                    Label("选择照片", systemImage: "photo.on.rectangle")
                        .frame(maxWidth: .infinity, minHeight: 52)
                        .contentShape(Rectangle())
                }
                .accessibilityIdentifier("photoPicker")
                .disabled(photos.isLoading)
                Rectangle().fill(.white.opacity(0.18)).frame(width: 1, height: 22)
                Button {
                    motion.manualDegrees = 0
                    motion.recalibrate()
                } label: {
                    Label("重新校准", systemImage: "scope")
                        .frame(maxWidth: .infinity, minHeight: 52)
                        .contentShape(Rectangle())
                }
                .accessibilityIdentifier("recalibrate")
                Rectangle().fill(.white.opacity(0.18)).frame(width: 1, height: 22)
                Button {
                    showsSettings = true
                } label: {
                    Image(systemName: "slider.horizontal.3")
                        .frame(width: 54, height: 52)
                        .contentShape(Rectangle())
                }
                .accessibilityLabel("更多设置")
                .accessibilityIdentifier("settingsButton")
            }
            .font(.subheadline.weight(.semibold))
            .buttonStyle(.plain)
            .foregroundStyle(.white)
            .background(.ultraThinMaterial, in: .capsule)
            .overlay { Capsule().strokeBorder(.white.opacity(0.16), lineWidth: 1) }
        }
        .padding(.horizontal, 22)
        .padding(.bottom, 14)
    }

    private var settings: some View {
        NavigationStack {
            Form {
                Section {
                    Toggle("手动预览", isOn: $motion.usesManualTilt)
                        .disabled(!motion.isMotionAvailable)
                        .accessibilityIdentifier("manualTilt")
                    if motion.usesManualTilt {
                        VStack(spacing: 12) {
                            HStack {
                                Text("转动角度")
                                Spacer()
                                Text(motion.manualDegrees, format: .number.precision(.fractionLength(0)))
                                    .monospacedDigit()
                                Text("°")
                            }
                            Slider(value: $motion.manualDegrees,
                                   in: -FoldMotionModel.maximumTiltDegrees...FoldMotionModel.maximumTiltDegrees, step: 1)
                                .accessibilityLabel("转动角度")
                                .accessibilityValue("\(Int(motion.manualDegrees.rounded())) 度")
                                .accessibilityIdentifier("tiltSlider")
                            HStack {
                                Button("左转 90°") { motion.manualDegrees = -FoldMotionModel.maximumTiltDegrees }
                                    .frame(minHeight: 44)
                                    .contentShape(Rectangle())
                                    .accessibilityIdentifier("tiltLeftLimit")
                                Spacer()
                                Button("右转 90°") { motion.manualDegrees = FoldMotionModel.maximumTiltDegrees }
                                    .frame(minHeight: 44)
                                    .contentShape(Rectangle())
                                    .accessibilityIdentifier("tiltRightLimit")
                            }
                                .font(.caption)
                                .foregroundStyle(.secondary)
                                .buttonStyle(.plain)
                        }
                    }
                } footer: {
                    Text(motion.motionErrorMessage ?? (motion.isMotionAvailable
                        ? "关闭手动预览，用手机的转动控制效果。轻点画面可隐藏或显示按钮。"
                        : "当前环境没有可用的姿态传感器，可以用滑块体验效果。"))
                }
                Section {
                    Button("恢复内置示例", systemImage: "rectangle.grid.1x2") {
                        samplePhoto = nil
                        photos.clear()
                        selection = nil
                        motion.manualDegrees = 0
                        motion.recalibrate()
                    }
                    .disabled(displayedPhoto == nil)
                    .accessibilityIdentifier("resetPhoto")
                } footer: {
                    Text("只读取你选中的照片，并保存在这台手机上。恢复示例会移除 App 内保存的副本。")
                }
                Section("关于折光") {
                    Text("手机像一片正在倾斜的磨砂玻璃，画面则留在原处。保持头部位置相对稳定，左右缓慢转动手机，效果会更明显。")
                    Text("基于 Elijah Semyonov 的 DuoLikeAnimation，遵循 MIT 许可证。")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            }
            .accessibilityIdentifier("settingsSheet")
            .navigationTitle("预览设置")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("完成") { showsSettings = false }
                        .accessibilityIdentifier("closeSettings")
                }
            }
        }
        .presentationDetents([.large])
    }

    private func updateMotion() {
        if scenePhase == .active && !showsPicker && !showsSettings {
            motion.start()
        } else {
            motion.stop()
        }
    }
}

/// Deterministic validation image; never accesses the photo library.
private enum PreviewPhoto {
    static func make() -> UIImage {
        let size = CGSize(width: 600, height: 1200)
        return UIGraphicsImageRenderer(size: size).image { context in
            UIColor(red: 0.08, green: 0.15, blue: 0.35, alpha: 1).setFill()
            context.fill(CGRect(origin: .zero, size: size))
            for index in 0..<7 {
                UIColor(hue: CGFloat(index) / 10, saturation: 0.7, brightness: 1, alpha: 1).setFill()
                context.cgContext.fillEllipse(in: CGRect(x: 60 + index * 28, y: 140 + index * 100, width: 340, height: 340))
            }
        }
    }
}
