import AppKit

final class SurfaceView:NSView {
    var color=NSColor.windowBackgroundColor
    override var wantsUpdateLayer:Bool {true}
    override func updateLayer() {layer?.backgroundColor=color.cgColor}
    init(_ color:NSColor = .windowBackgroundColor) {super.init(frame:.zero);self.color=color;wantsLayer=true}
    required init?(coder:NSCoder) {fatalError()}
}

final class NavigationButton:NSButton {
    var selected=false {didSet{contentTintColor=selected ? .controlAccentColor:.secondaryLabelColor;needsDisplay=true}}
    init(title:String,symbol:String,target:AnyObject,action:Selector) {
        super.init(frame:.zero);self.title=title;self.target=target;self.action=action
        isBordered=false;bezelStyle = .regularSquare;alignment = .left
        font = .systemFont(ofSize:13,weight:.medium)
        image=NSImage(systemSymbolName:symbol,accessibilityDescription:nil)
        imagePosition = .imageLeading;imageHugsTitle=true
        heightAnchor.constraint(equalToConstant:36).isActive=true
        contentTintColor = .secondaryLabelColor;setAccessibilityLabel(title.trimmingCharacters(in:.whitespaces))
    }
    required init?(coder:NSCoder){fatalError()}
    override func draw(_ dirtyRect:NSRect) {
        if selected {NSColor.controlAccentColor.withAlphaComponent(0.13).setFill();NSBezierPath(roundedRect:bounds,xRadius:7,yRadius:7).fill()}
        super.draw(dirtyRect)
    }
}

final class HingePreviewView:NSView {
    var lidAngle=110.0 {didSet{needsDisplay=true}}
    override func draw(_ dirtyRect:NSRect) {
        let x=bounds.midX-25,y=28.0,length=82.0
        let rad=min(150,max(15,lidAngle)) * .pi/180
        let end=NSPoint(x:x+cos(rad)*length,y:y+sin(rad)*length)
        let guide=NSBezierPath();guide.appendArc(withCenter:NSPoint(x:x,y:y),radius:30,startAngle:0,endAngle:CGFloat(min(150,max(15,lidAngle))))
        guide.lineWidth=1;NSColor.controlAccentColor.withAlphaComponent(0.25).setStroke();guide.stroke()
        let lid=NSBezierPath();lid.move(to:NSPoint(x:x,y:y));lid.line(to:end);lid.lineWidth=6;lid.lineCapStyle = .round
        NSColor.labelColor.withAlphaComponent(0.8).setStroke();lid.stroke()
        let glow=NSBezierPath();glow.move(to:NSPoint(x:x+3,y:y+6));glow.line(to:NSPoint(x:end.x+3,y:end.y-4));glow.lineWidth=2;glow.lineCapStyle = .round
        NSColor.controlAccentColor.setStroke();glow.stroke()
        let base=NSBezierPath();base.move(to:NSPoint(x:x-5,y:y-4));base.line(to:NSPoint(x:x+104,y:y-4));base.lineWidth=5;base.lineCapStyle = .round
        NSColor.tertiaryLabelColor.setStroke();base.stroke()
        NSColor.controlAccentColor.setFill();NSBezierPath(ovalIn:NSRect(x:x-4,y:y-4,width:8,height:8)).fill()
    }
}

func layoutStack(_ views:[NSView],axis:NSUserInterfaceLayoutOrientation = .vertical,spacing:CGFloat=12,alignment:NSLayoutConstraint.Attribute = .leading)->NSStackView {
    let stack=NSStackView(views:views);stack.orientation=axis;stack.spacing=spacing;stack.alignment=alignment;return stack
}
func flexibleSpace()->NSView {let view=NSView();view.setContentHuggingPriority(.init(1),for:.horizontal);return view}
func hairline()->NSBox {let line=NSBox();line.boxType = .separator;return line}
