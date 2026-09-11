import AppKit
import ScreenCaptureKit
import CoreMedia
import CoreVideo

final class DesktopCapture: NSObject, SCStreamOutput, SCStreamDelegate {
    let mailbox: FrameMailbox
    var onFailure: ((String)->Void)?
    var onFirstFrame: (()->Void)?
    private var stream: SCStream?
    private let queue=DispatchQueue(label:"com.zksaga.foldlight.capture",qos:.userInteractive)
    private let lock=NSLock()
    private var active=false,first=true
    init(mailbox: FrameMailbox) { self.mailbox=mailbox }

    @MainActor func start(screen: NSScreen, fps: Int) async throws {
        let content=try await SCShareableContent.excludingDesktopWindows(false,onScreenWindowsOnly:false)
        let id=(screen.deviceDescription[NSDeviceDescriptionKey("NSScreenNumber")] as? NSNumber)?.uint32Value
        guard let display=content.displays.first(where:{$0.displayID==id}) else {
            throw NSError(domain:"Foldlight",code:1,userInfo:[NSLocalizedDescriptionKey:"Display unavailable"])
        }
        // Exclude our entire process, including future settings/overlay windows.
        guard let ownApp=content.applications.first(where:{$0.processID==ProcessInfo.processInfo.processIdentifier}) else {
            throw NSError(domain:"Foldlight",code:2,userInfo:[NSLocalizedDescriptionKey:"Cannot exclude Foldlight from capture"])
        }
        let filter=SCContentFilter(display:display,excludingApplications:[ownApp],exceptingWindows:[])
        let configuration=SCStreamConfiguration()
        let size=FoldMath.pixelSize(width:screen.frame.width,height:screen.frame.height,scale:screen.backingScaleFactor)
        configuration.width=size.0;configuration.height=size.1
        configuration.minimumFrameInterval=CMTime(value:1,timescale:Int32(fps))
        configuration.queueDepth=3;configuration.pixelFormat=kCVPixelFormatType_32BGRA
        configuration.showsCursor=false;configuration.capturesAudio=false
        configuration.colorSpaceName=CGColorSpace.sRGB as CFString
        let stream=SCStream(filter:filter,configuration:configuration,delegate:self)
        try stream.addStreamOutput(self,type:.screen,sampleHandlerQueue:queue)
        self.stream=stream;setActive(true)
        do { try await stream.startCapture() }
        catch { setActive(false);self.stream=nil;throw error }
    }
    private func setActive(_ value: Bool) { lock.lock();active=value;first=true;lock.unlock() }
    @MainActor func stop() async {
        setActive(false)
        let old=stream;stream=nil
        try? await old?.stopCapture();mailbox.clear()
    }
    func stream(_ stream: SCStream,didOutputSampleBuffer sampleBuffer: CMSampleBuffer,of type: SCStreamOutputType) {
        guard type == .screen,CMSampleBufferIsValid(sampleBuffer),
              let attachments=CMSampleBufferGetSampleAttachmentsArray(sampleBuffer,createIfNecessary:false) as? [[SCStreamFrameInfo:Any]],
              let status=attachments.first?[.status] as? Int,status==SCFrameStatus.complete.rawValue,
              let image=CMSampleBufferGetImageBuffer(sampleBuffer) else { return }
        lock.lock()
        guard active else { lock.unlock();return }
        let notify=first;first=false;mailbox.put(image);lock.unlock()
        if notify { DispatchQueue.main.async { [weak self] in self?.onFirstFrame?() } }
    }
    func stream(_ stream: SCStream,didStopWithError error: Error) {
        lock.lock();let notify=active;active=false;lock.unlock()
        if notify { DispatchQueue.main.async { [weak self] in self?.onFailure?(error.localizedDescription) } }
    }
}
