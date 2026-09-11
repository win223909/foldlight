import AppKit
setbuf(stdout,nil)
MainActor.assumeIsolated {
    let application=NSApplication.shared
    let delegate=AppDelegate()
    application.delegate=delegate
    application.run()
}
