import Foundation
import ImageIO
import Observation
import PhotosUI
import SwiftUI
import UIKit
import UniformTypeIdentifiers

/// Keeps only a downsampled copy of the one photo chosen in the system picker.
@Observable @MainActor
final class PhotoStore {
    private(set) var image: UIImage?
    private(set) var isLoading = false
    /// The view can set this to nil when dismissing its error alert.
    var errorMessage: String?

    @ObservationIgnored private let storage: PhotoStorage
    @ObservationIgnored private var activeRevision: UInt64 = 0

    /// Pass a complete JPEG file URL to isolate previews and tests from the user's photo.
    convenience init(storageURL: URL? = nil) {
        let url = storageURL ?? FileManager.default.urls(
            for: .applicationSupportDirectory, in: .userDomainMask
        )[0].appendingPathComponent("DuoLikeAnimation", isDirectory: true)
            .appendingPathComponent("photo.jpg")
        self.init(storage: PhotoStorage(url: url))
    }

    init(storage: PhotoStorage) {
        self.storage = storage
        let revision = activeRevision
        isLoading = true

        Task { [weak self, storage] in
            let result = await Task.detached(priority: .userInitiated) {
                Result { try storage.restore() }
            }.value
            guard let self, self.activeRevision == revision else { return }
            self.isLoading = false
            switch result {
            case .success(let restored):
                self.image = restored.map { UIImage(cgImage: $0) }
            case .failure:
                self.errorMessage = "上次保存的照片无法读取，请重新选择照片。"
            }
        }
    }

    func load(_ item: PhotosPickerItem) async {
        await replacePhoto { try await item.loadTransferable(type: Data.self) }
    }

    /// Also allows importing already-selected image data without requesting photo-library access.
    func load(data: Data) async {
        await replacePhoto { data }
    }

    func clear() {
        activeRevision &+= 1
        let revision = activeRevision
        let storageRevision = storage.beginChange()
        image = nil
        isLoading = false
        errorMessage = nil
        Task { [weak self, storage] in
            do {
                try await Task.detached(priority: .utility) {
                    try storage.remove(ifCurrent: storageRevision)
                }.value
            } catch {
                guard let self, self.activeRevision == revision else { return }
                self.errorMessage = "无法移除本机保存的照片，请再试一次。"
            }
        }
    }

    private func replacePhoto(from readData: () async throws -> Data?) async {
        // Loading may fail or be cancelled. It supersedes older decodes, but must not
        // invalidate a pending disk operation for the last accepted photo or clear.
        activeRevision &+= 1
        let revision = activeRevision
        isLoading = true
        errorMessage = nil
        var acceptedImage = false
        defer {
            if activeRevision == revision { isLoading = false }
        }

        do {
            try Task.checkCancellation()
            guard let data = try await readData() else { throw PhotoDecodingError.invalidImage }
            try Task.checkCancellation()
            let prepared = try await Task.detached(priority: .userInitiated) {
                try PhotoImageProcessor.prepare(data)
            }.value
            guard activeRevision == revision else { return }
            try Task.checkCancellation()

            // Accept the decoded image before saving. A subsequent selection now keeps this
            // known-good image if it is cancelled or cannot be read.
            let storageRevision = storage.beginChange()
            image = UIImage(cgImage: prepared.image)
            acceptedImage = true
            try await Task.detached(priority: .utility) { [storage] in
                try storage.save(prepared.jpegData, ifCurrent: storageRevision)
            }.value
        } catch {
            guard activeRevision == revision, !Task.isCancelled else { return }
            let cocoaError = error as NSError
            guard !(error is CancellationError),
                  !(cocoaError.domain == NSCocoaErrorDomain && cocoaError.code == NSUserCancelledError),
                  !(cocoaError.domain == NSURLErrorDomain && cocoaError.code == NSURLErrorCancelled)
            else { return }
            errorMessage = acceptedImage
                ? "照片已显示，但暂时无法保存到本机。请重新选择后再试。"
                : "这张照片暂时无法读取。请确认照片已下载到手机，或换一张照片。"
        }
    }
}

nonisolated enum PhotoDecodingError: Error {
    case invalidImage
    case cannotEncode
}

nonisolated struct PreparedPhoto: Sendable {
    let image: CGImage
    let jpegData: Data
}

nonisolated enum PhotoImageProcessor {
    static let maximumPixelDimension = 2_400

    static func downsample(_ data: Data) throws -> CGImage {
        guard let source = CGImageSourceCreateWithData(data as CFData, [
            kCGImageSourceShouldCache: false,
        ] as CFDictionary),
              let image = CGImageSourceCreateThumbnailAtIndex(source, 0, [
                kCGImageSourceCreateThumbnailFromImageAlways: true,
                kCGImageSourceCreateThumbnailWithTransform: true,
                kCGImageSourceThumbnailMaxPixelSize: maximumPixelDimension,
                kCGImageSourceShouldCacheImmediately: true,
              ] as CFDictionary)
        else { throw PhotoDecodingError.invalidImage }
        return image
    }

    static func prepare(_ data: Data) throws -> PreparedPhoto {
        let image = try downsample(data)
        let encoded = NSMutableData()
        guard let destination = CGImageDestinationCreateWithData(
            encoded as CFMutableData, UTType.jpeg.identifier as CFString, 1, nil
        ) else { throw PhotoDecodingError.cannotEncode }
        // The transform above is baked into the pixels. Re-encoding only those pixels also
        // leaves out source metadata such as location and the original orientation tag.
        CGImageDestinationAddImage(destination, image, [
            kCGImageDestinationLossyCompressionQuality: 0.9,
        ] as CFDictionary)
        guard CGImageDestinationFinalize(destination) else { throw PhotoDecodingError.cannotEncode }
        return PreparedPhoto(image: image, jpegData: encoded as Data)
    }
}

/// Synchronization covers the revision and the final rename only. Image decoding and file
/// writing happen on background tasks; stale writes cannot replace a newer selection or clear.
nonisolated final class PhotoStorage: @unchecked Sendable {
    private let url: URL
    private let lock = NSLock()
    private var revision: UInt64 = 0

    init(url: URL) { self.url = url }

    /// Call only when accepting an image or clearing it, never merely starting an import.
    func beginChange() -> UInt64 {
        lock.withLock {
            revision &+= 1
            return revision
        }
    }

    func restore() throws -> CGImage? {
        guard FileManager.default.fileExists(atPath: url.path) else { return nil }
        return try PhotoImageProcessor.downsample(Data(contentsOf: url, options: .mappedIfSafe))
    }

    func save(_ data: Data, ifCurrent expectedRevision: UInt64) throws {
        let fileManager = FileManager.default
        var directory = url.deletingLastPathComponent()
        try fileManager.createDirectory(at: directory, withIntermediateDirectories: true)
        var values = URLResourceValues()
        values.isExcludedFromBackup = true
        try directory.setResourceValues(values)
        let temporaryURL = directory.appendingPathComponent(".photo-\(UUID().uuidString).jpg")
        defer { try? fileManager.removeItem(at: temporaryURL) }
        try data.write(to: temporaryURL, options: .atomic)
        try lock.withLock {
            guard revision == expectedRevision else { return }
            if fileManager.fileExists(atPath: url.path) {
                _ = try fileManager.replaceItemAt(url, withItemAt: temporaryURL)
            } else {
                try fileManager.moveItem(at: temporaryURL, to: url)
            }
        }
    }

    func remove(ifCurrent expectedRevision: UInt64) throws {
        try lock.withLock {
            guard revision == expectedRevision, FileManager.default.fileExists(atPath: url.path) else { return }
            try FileManager.default.removeItem(at: url)
        }
    }
}
