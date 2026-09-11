import AppKit

let side = 1024
let bitmap = NSBitmapImageRep(bitmapDataPlanes: nil, pixelsWide: side, pixelsHigh: side, bitsPerSample: 8, samplesPerPixel: 4, hasAlpha: true, isPlanar: false, colorSpaceName: .deviceRGB, bytesPerRow: 0, bitsPerPixel: 0)!
let context = NSGraphicsContext(bitmapImageRep: bitmap)!
NSGraphicsContext.saveGraphicsState()
NSGraphicsContext.current = context
let bounds = NSRect(x: 0, y: 0, width: side, height: side)
NSGradient(colors: [NSColor(red: 0.03, green: 0.08, blue: 0.17, alpha: 1), NSColor(red: 0.15, green: 0.19, blue: 0.4, alpha: 1)])!.draw(in: bounds, angle: 45)
let glow = NSBezierPath(ovalIn: NSRect(x: 370, y: 290, width: 420, height: 420))
NSGradient(colors: [NSColor(red: 1, green: 0.77, blue: 0.6, alpha: 1), NSColor(red: 0.9, green: 0.37, blue: 0.55, alpha: 1)])!.draw(in: glow, angle: -50)
for i in 0..<5 {
    let x = CGFloat(185 + i * 74)
    let pane = NSBezierPath()
    pane.move(to: NSPoint(x: x, y: 200))
    pane.line(to: NSPoint(x: x + 240, y: 272))
    pane.line(to: NSPoint(x: x + 240, y: 822))
    pane.line(to: NSPoint(x: x, y: 750))
    pane.close()
    NSColor(red: 0.56, green: 0.77, blue: 1, alpha: 0.11).setFill()
    pane.fill()
    NSColor(white: 1, alpha: CGFloat(i == 4 ? 0.7 : 0.30)).setStroke()
    pane.lineWidth = i == 4 ? 5 : 2
    pane.stroke()
}
NSGraphicsContext.restoreGraphicsState()
let opaque = CGContext(data: nil, width: side, height: side, bitsPerComponent: 8, bytesPerRow: side * 4, space: CGColorSpaceCreateDeviceRGB(), bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue)!
opaque.draw(bitmap.cgImage!, in: CGRect(x: 0, y: 0, width: side, height: side))
try NSBitmapImageRep(cgImage: opaque.makeImage()!).representation(using: .png, properties: [:])!.write(to: URL(fileURLWithPath: CommandLine.arguments[1]))
