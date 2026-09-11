import Foundation
import ImageIO
import Testing
import UIKit
import UniformTypeIdentifiers
@testable import DuoLikeAnimation

@MainActor
struct PhotoStoreTests {
    @Test func selectedPhotoIsDownsampledAndItsOrientationSurvivesSaving() async throws {
        let url = temporaryPhotoURL()
        defer { try? FileManager.default.removeItem(at: url.deletingLastPathComponent()) }
        let store = PhotoStore(storageURL: url)

        // A camera JPEG whose pixels are landscape but whose EXIF orientation is portrait.
        await store.load(data: try makeJPEG(width: 3_000, height: 1_500, orientation: 6))
        let image = try #require(store.image)
        #expect(image.imageOrientation == .up)
        #expect(image.cgImage?.width == 1_200)
        #expect(image.cgImage?.height == 2_400)
        #expect(store.errorMessage == nil)

        let persisted = try Data(contentsOf: url)
        let reopened = try #require(UIImage(data: persisted))
        #expect(reopened.imageOrientation == .up)
        #expect(reopened.cgImage?.width == 1_200)
        #expect(reopened.cgImage?.height == 2_400)
    }

    @Test func unreadableSelectionKeepsPreviouslyDisplayedAndSavedPhoto() async throws {
        let url = temporaryPhotoURL()
        defer { try? FileManager.default.removeItem(at: url.deletingLastPathComponent()) }
        let store = PhotoStore(storageURL: url)
        await store.load(data: try makeJPEG(width: 80, height: 120))
        let originalImage = try #require(store.image)
        let originalFile = try Data(contentsOf: url)

        await store.load(data: Data("This is not an image".utf8))

        #expect(store.image === originalImage)
        #expect(try Data(contentsOf: url) == originalFile)
        #expect(store.errorMessage != nil)
        #expect(!store.isLoading)
    }

    @Test func cancelledSelectionKeepsPreviouslyDisplayedAndSavedPhoto() async throws {
        let url = temporaryPhotoURL()
        defer { try? FileManager.default.removeItem(at: url.deletingLastPathComponent()) }
        let store = PhotoStore(storageURL: url)
        await store.load(data: try makeJPEG(width: 80, height: 120))
        let originalImage = try #require(store.image)
        let originalFile = try Data(contentsOf: url)

        let loading = Task { await store.load(data: Data()) }
        loading.cancel()
        await loading.value

        #expect(store.image === originalImage)
        #expect(try Data(contentsOf: url) == originalFile)
        #expect(store.errorMessage == nil)
        #expect(!store.isLoading)
    }

    @Test func oldSaveCannotReinstatePhotoAfterClear() throws {
        let url = temporaryPhotoURL()
        defer { try? FileManager.default.removeItem(at: url.deletingLastPathComponent()) }
        let storage = PhotoStorage(url: url)
        let earlierSelection = storage.beginChange()
        let clear = storage.beginChange()
        try storage.remove(ifCurrent: clear)
        try storage.save(makeJPEG(width: 80, height: 120), ifCurrent: earlierSelection)
        #expect(!FileManager.default.fileExists(atPath: url.path))
    }

    @Test(arguments: [false, true])
    func unsuccessfulSelectionDoesNotCancelPendingClear(isCancelled: Bool) async throws {
        let url = temporaryPhotoURL()
        defer { try? FileManager.default.removeItem(at: url.deletingLastPathComponent()) }
        let storage = PhotoStorage(url: url)
        let store = PhotoStore(storage: storage)
        await store.load(data: try makeJPEG(width: 80, height: 120))

        // Pause the clear at its disk boundary, then let another selection fail before
        // deletion runs. Restarting must still show the demo rather than the old photo.
        let clear = storage.beginChange()
        await importUnsuccessfully(into: store, isCancelled: isCancelled)
        try storage.remove(ifCurrent: clear)

        #expect(try PhotoStorage(url: url).restore() == nil)
    }

    @Test(arguments: [false, true])
    func unsuccessfulSelectionDoesNotCancelPendingAcceptedPhotoSave(isCancelled: Bool) async throws {
        let url = temporaryPhotoURL()
        defer { try? FileManager.default.removeItem(at: url.deletingLastPathComponent()) }
        let storage = PhotoStorage(url: url)
        let store = PhotoStore(storage: storage)
        let photo = try makeJPEG(width: 120, height: 80)

        // An accepted photo is waiting for background disk IO. A later failed import
        // must not discard that accepted photo's pending save.
        let acceptedPhoto = storage.beginChange()
        await importUnsuccessfully(into: store, isCancelled: isCancelled)
        try storage.save(photo, ifCurrent: acceptedPhoto)

        let restored = try #require(try PhotoStorage(url: url).restore())
        #expect(restored.width == 120)
        #expect(restored.height == 80)
    }

    @Test func delayedClearCannotDeleteNewerAcceptedPhoto() throws {
        let url = temporaryPhotoURL()
        defer { try? FileManager.default.removeItem(at: url.deletingLastPathComponent()) }
        let storage = PhotoStorage(url: url)
        let original = storage.beginChange()
        try storage.save(makeJPEG(width: 80, height: 120), ifCurrent: original)
        let clear = storage.beginChange()
        let replacement = storage.beginChange()
        try storage.save(makeJPEG(width: 120, height: 80), ifCurrent: replacement)

        try storage.remove(ifCurrent: clear)

        let restored = try #require(try PhotoStorage(url: url).restore())
        #expect(restored.width == 120)
        #expect(restored.height == 80)
    }

    private func temporaryPhotoURL() -> URL {
        FileManager.default.temporaryDirectory
            .appendingPathComponent("PhotoStoreTests-\(UUID().uuidString)", isDirectory: true)
            .appendingPathComponent("photo.jpg")
    }

    private func importUnsuccessfully(into store: PhotoStore, isCancelled: Bool) async {
        let loading = Task { await store.load(data: Data("Unreadable replacement".utf8)) }
        if isCancelled { loading.cancel() }
        await loading.value
    }

    private func makeJPEG(width: Int, height: Int, orientation: Int = 1) throws -> Data {
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        format.opaque = true
        let size = CGSize(width: CGFloat(width), height: CGFloat(height))
        let image = UIGraphicsImageRenderer(size: size, format: format).image { context in
            UIColor.systemRed.setFill()
            context.fill(CGRect(origin: .zero, size: size))
            UIColor.systemBlue.setFill()
            context.fill(CGRect(x: CGFloat(width) / 2, y: 0, width: CGFloat(width) / 2, height: CGFloat(height)))
        }
        let encoded = NSMutableData()
        let destination = try #require(CGImageDestinationCreateWithData(
            encoded as CFMutableData, UTType.jpeg.identifier as CFString, 1, nil
        ))
        CGImageDestinationAddImage(destination, try #require(image.cgImage), [
            kCGImagePropertyOrientation: orientation,
        ] as CFDictionary)
        #expect(CGImageDestinationFinalize(destination))
        return encoded as Data
    }
}
