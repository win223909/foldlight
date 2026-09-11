//
//  FoldMotionModel.swift
//  DuoLikeAnimation
//

import CoreMotion
import Foundation
import Observation
import simd

/// Derives the device's tilt around the screen-space Y axis from Core Motion attitude,
/// relative to a calibrated "zero tilt" pose.
@Observable @MainActor
final class FoldMotionModel {
    static let maximumTiltDegrees: Double = 90

    /// Tilt fed to the shader, in radians. Positive means the right edge is farther from the viewer.
    var tiltAngle: Double {
        Self.clampedTilt(usesManualTilt ? manualDegrees * .pi / 180 : motionTilt)
    }

    var usesManualTilt: Bool {
        didSet {
            guard usesManualTilt != oldValue else { return }
            if usesManualTilt {
                stopMotionUpdates()
            } else if wantsMotionUpdates {
                startMotionUpdates()
            }
        }
    }
    var manualDegrees: Double
    private(set) var isMotionAvailable: Bool
    private(set) var motionErrorMessage: String?

    private(set) var motionTilt: Double = 0

    @ObservationIgnored private let motionManager = CMMotionManager()
    /// The view's lifecycle request is independent of the manual/automatic choice.
    @ObservationIgnored private var wantsMotionUpdates = false
    /// Ignore callbacks that were queued before a pause or a change of mode.
    @ObservationIgnored private var motionSession = 0
    @ObservationIgnored private var reference: simd_double3x3?
    /// Whether `CMRotationMatrix` rows hold the device axes expressed in the reference frame.
    /// Resolved empirically against the gravity vector on the first informative sample.
    @ObservationIgnored private var rowsAreDeviceAxes: Bool?
    /// Fraction of the remaining error closed per sample. Kept high: the attitude is already fused,
    /// and every extra frame of filtering is visible as lag between the hand and the screen.
    @ObservationIgnored private let smoothing = 0.7
    /// How far ahead to extrapolate with the gyroscope, to cover sensor and display latency.
    @ObservationIgnored private let predictionInterval = 0.04

    init() {
        // Launch-time override for simulator runs: `-tiltDegrees 20` or TILT_DEGREES=-20.
        let defaults = UserDefaults.standard
        let environment = ProcessInfo.processInfo.environment
        let initialDegrees = environment["TILT_DEGREES"].flatMap(Double.init) ?? defaults.double(forKey: "tiltDegrees")
        manualDegrees = Self.clampedTilt(initialDegrees * .pi / 180) * 180 / .pi
        isMotionAvailable = motionManager.isDeviceMotionAvailable
        #if targetEnvironment(simulator)
        usesManualTilt = true
        #else
        usesManualTilt = defaults.bool(forKey: "manualTilt") || !motionManager.isDeviceMotionAvailable
        #endif
    }

    func start() {
        wantsMotionUpdates = true
        startMotionUpdates()
    }

    func stop() {
        wantsMotionUpdates = false
        stopMotionUpdates()
    }

    private func startMotionUpdates() {
        guard wantsMotionUpdates, !usesManualTilt, !motionManager.isDeviceMotionActive else { return }
        isMotionAvailable = motionManager.isDeviceMotionAvailable
        guard isMotionAvailable else {
            fallBackToManual("此设备暂时无法读取姿态，请使用手动滑块。")
            return
        }

        motionErrorMessage = nil
        recalibrate()
        motionSession += 1
        let session = motionSession
        // Gyro-only reference frame: the magnetometer-corrected variants trade latency for
        // long-term yaw stability, and yaw is exactly the axis this effect tracks.
        motionManager.deviceMotionUpdateInterval = 1.0 / 120.0
        motionManager.startDeviceMotionUpdates(using: .xArbitraryZVertical, to: .main) { [weak self] motion, error in
            MainActor.assumeIsolated {
                guard let self, self.wantsMotionUpdates, !self.usesManualTilt,
                      self.motionSession == session else { return }
                if error != nil {
                    self.fallBackToManual("姿态读取中断，已切换为手动模式。可关闭手动模式重试。")
                    return
                }
                guard let motion else { return }
                self.process(motion)
            }
        }
    }

    private func stopMotionUpdates() {
        motionSession += 1
        motionManager.stopDeviceMotionUpdates()
    }

    private func fallBackToManual(_ message: String) {
        manualDegrees = Self.clampedTilt(motionTilt) * 180 / .pi
        motionErrorMessage = message
        usesManualTilt = true
    }

    /// Makes the current pose the zero-tilt pose: the plane the UI stays in.
    func recalibrate() {
        reference = nil
        motionTilt = 0
    }

    private func process(_ motion: CMDeviceMotion) {
        let m = motion.attitude.rotationMatrix
        let gravity = SIMD3(motion.gravity.x, motion.gravity.y, motion.gravity.z)
        let rate = motion.rotationRate
        guard m.m11.isFinite, m.m12.isFinite, m.m13.isFinite,
              m.m21.isFinite, m.m22.isFinite, m.m23.isFinite,
              m.m31.isFinite, m.m32.isFinite, m.m33.isFinite,
              gravity.x.isFinite, gravity.y.isFinite, gravity.z.isFinite,
              rate.x.isFinite, rate.y.isFinite, rate.z.isFinite,
              simd_length_squared(gravity) > 1e-8 else {
            fallBackToManual("姿态数据异常，已切换为手动模式。可关闭手动模式重试。")
            return
        }

        let deviceToReference = deviceToReferenceMatrix(motion)
        guard let reference else {
            reference = deviceToReference
            return
        }

        // Current device axes expressed in the calibrated device frame.
        let relative = reference.transpose * deviceToReference
        let normal = relative.columns.2                     // current screen normal
        // The iPhone target is portrait-only, so screen X/Y are device X/Y.
        let measured = atan2(normal.x, normal.z)

        // Extrapolate along the rotation rate around the screen's Y axis.
        let predicted = Self.clampedTilt(measured + rate.y * predictionInterval)

        motionTilt += (predicted - motionTilt) * smoothing
    }

    private static func clampedTilt(_ angle: Double) -> Double {
        guard angle.isFinite else { return 0 }
        let limit = maximumTiltDegrees * .pi / 180
        return min(max(angle, -limit), limit)
    }

    /// Rotation taking device-frame vectors to reference-frame vectors (column-vector convention).
    private func deviceToReferenceMatrix(_ motion: CMDeviceMotion) -> simd_double3x3 {
        let m = motion.attitude.rotationMatrix
        let asRows = simd_double3x3(columns: (
            SIMD3(m.m11, m.m21, m.m31),
            SIMD3(m.m12, m.m22, m.m32),
            SIMD3(m.m13, m.m23, m.m33)
        ))

        if rowsAreDeviceAxes == nil {
            // Gravity is reported in the device frame and points down (-Z in a Z-vertical reference).
            // Compare it against what each matrix convention predicts and latch the better match.
            let gravity = simd_normalize(SIMD3(motion.gravity.x, motion.gravity.y, motion.gravity.z))
            let down = SIMD3(0.0, 0.0, -1.0)
            let rowsScore = simd_dot(gravity, asRows * down)
            let columnsScore = simd_dot(gravity, asRows.transpose * down)
            if abs(rowsScore - columnsScore) > 0.2 {
                rowsAreDeviceAxes = rowsScore > columnsScore
            }
        }
        return (rowsAreDeviceAxes ?? true) ? asRows.transpose : asRows
    }
}
