import AppKit
import MetalKit
import ServiceManagement

@MainActor final class AppDelegate: NSObject, NSApplicationDelegate {
    private let defaults=UserDefaults.standard
    private let sensor=LidSensor()
    private let hotkey=HotKey()
    private var statusItem: NSStatusItem!
    private var window: NSWindow?
    private var overlay: OverlayWindow?
    private var renderer: DesktopRenderer?
    private var capture: DesktopCapture?
    private var selectedScreen: NSScreen?
    private var enabled=false,starting=false,firstFrame=false
    private var generation=0
    private var raw: Double?,reference: Double?
    private var sampleTime=0.0
    private var highPrecision=false
    private var screenPermission=false
    private var manual=false,manualAngle=0.0
    private var message=""
    private var stateLabel: NSTextField?,sensorLabel: NSTextField?,angleLabel: NSTextField?,blurLabel: NSTextField?
    private var settingsPage=0
    private var hingePreview:HingePreviewView?,angleReadout:NSTextField?,sidebarStatus:NSTextField?,precisionLabel:NSTextField?
    private var metricsLabel: NSTextField?
    private var lastCompletedCount=0,lastGPUTotal=0.0
    private var lastCaptureCount=0,lastRenderCount=0,lastMetricsTime=ProcessInfo.processInfo.systemUptime
    private var startButton: NSButton?,angleSlider: NSSlider?,modeControl: NSSegmentedControl?
    private var fpsPopup: NSPopUpButton?,displayPopup: NSPopUpButton?
    private var observers=[NSObjectProtocol]()
    private var blur: Double { didSet { renderer?.blur=blur;defaults.set(blur,forKey:"blur") } }
    private var fps: Int { didSet { defaults.set(fps,forKey:"fps") } }
    private var chinese: Bool { didSet { defaults.set(chinese ? "zh":"en",forKey:"language") } }
    override init() {
        let d=UserDefaults.standard
        blur=d.object(forKey:"blur")==nil ? 0.08:min(1,max(0,d.double(forKey:"blur")))
        fps=d.object(forKey:"fps")==nil ? 120:(d.integer(forKey:"fps")==120 ? 120:60)
        chinese=(d.string(forKey:"language") ?? Locale.preferredLanguages.first ?? "en").hasPrefix("zh")
        super.init()
    }
    func tr(_ zh: String,_ en: String)->String { chinese ? zh:en }
    func applicationDidFinishLaunching(_ notification: Notification) {
        NSApp.setActivationPolicy(.accessory)
        screenPermission=CGPreflightScreenCaptureAccess()
        statusItem=NSStatusBar.system.statusItem(withLength:NSStatusItem.variableLength)
        statusItem.button?.image=NSImage(systemSymbolName:"rectangle.on.rectangle.angled",accessibilityDescription:"Foldlight")
        statusItem.button?.toolTip=tr("折光 · 桌面合盖效果","Foldlight · Live desktop effect")
        rebuildMenu()
        sensor.onSample={ [weak self] value,time in
            guard let self else { return }
            self.raw=value;self.sampleTime=time
            if self.reference==nil,let value { self.reference=value }
            if !self.manual {self.applyAngle()}
        }
        sensor.onPrecision={ [weak self] fine in
            guard let self else {return};self.highPrecision=fine;self.renderer?.responseSeconds=fine ? 0.018:0.06
        }
        let uiTimer=Timer(timeInterval:0.1,repeats:true) { [weak self] _ in
            MainActor.assumeIsolated { if self?.window?.isVisible==true {self?.updateLabels()} }
        }
        RunLoop.main.add(uiTimer,forMode:.common)
        hotkey.action={ [weak self] in self?.toggle() }
        let nc=NotificationCenter.default
        observers.append(nc.addObserver(forName:NSApplication.didChangeScreenParametersNotification,object:nil,queue:.main) { [weak self] _ in
            MainActor.assumeIsolated { self?.displayChanged() }
        })
        let workspace=NSWorkspace.shared.notificationCenter
        for name in [NSWorkspace.willSleepNotification,NSWorkspace.screensDidSleepNotification,NSWorkspace.sessionDidResignActiveNotification] {
            observers.append(workspace.addObserver(forName:name,object:nil,queue:.main) { [weak self] _ in
                MainActor.assumeIsolated { self?.suspend() }
            })
        }
        for name in [NSWorkspace.didWakeNotification,NSWorkspace.screensDidWakeNotification,NSWorkspace.sessionDidBecomeActiveNotification] {
            observers.append(workspace.addObserver(forName:name,object:nil,queue:.main) { [weak self] _ in
                MainActor.assumeIsolated { self?.resume() }
            })
        }
        sensor.start()
        let metricsTimer=Timer(timeInterval:1,repeats:true) { [weak self] _ in
            MainActor.assumeIsolated { self?.updateMetrics() }
        }
        RunLoop.main.add(metricsTimer,forMode:.common)
        if defaults.bool(forKey:"enabled"),CGPreflightScreenCaptureAccess() { start() } else { showSettings() }
        if ProcessInfo.processInfo.arguments.contains("--self-test") { runSelfTest() }
        if ProcessInfo.processInfo.arguments.contains("--benchmark") { runBenchmark() }
        if ProcessInfo.processInfo.arguments.contains("--transition-benchmark") { runTransitionBenchmark() }
    }
    func applicationDidBecomeActive(_ notification:Notification) { updateSettingsWindowLevel() }
    func applicationDidResignActive(_ notification:Notification) { window?.level = .normal }
    private func updateSettingsWindowLevel() {
        // Keep controls reachable above our opaque effect only while Foldlight is
        // active. Switching apps restores ordinary window ordering immediately.
        window?.level = NSApp.isActive && renderer?.isEffectVisible == true
            ? NSWindow.Level(rawValue:Int(CGWindowLevelForKey(.statusWindow))+2):.normal
    }
    func applicationShouldTerminateAfterLastWindowClosed(_ sender: NSApplication)->Bool { false }
    func applicationShouldHandleReopen(_ sender: NSApplication,hasVisibleWindows flag: Bool)->Bool { showSettings();return true }
    func applicationWillTerminate(_ notification: Notification) {
        overlay?.orderOut(nil);sensor.stop()
        // Closing the process also closes ScreenCaptureKit's stream and all GPU resources.
    }
    private func preferredScreen()->NSScreen? {
        let saved=defaults.object(forKey:"displayID") as? NSNumber
        return NSScreen.screens.first(where:{($0.deviceDescription[NSDeviceDescriptionKey("NSScreenNumber")] as? NSNumber)==saved})
            ?? NSScreen.screens.first(where:{ screen in
                let id=(screen.deviceDescription[NSDeviceDescriptionKey("NSScreenNumber")] as? NSNumber)?.uint32Value ?? 0
                return CGDisplayIsBuiltin(id) != 0
            }) ?? NSScreen.main
    }
    private func effectiveFPS(_ screen: NSScreen)->Int { min(fps,max(30,screen.maximumFramesPerSecond)) }
    private func rebuildMenu() {
        let menu=NSMenu()
        func item(_ title:String,_ selector:Selector,_ key:String="") {
            let i=NSMenuItem(title:title,action:selector,keyEquivalent:key);i.target=self;menu.addItem(i)
        }
        item(tr("折光设置…","Foldlight Settings…"),#selector(showSettings))
        item(enabled||starting ? tr("暂停效果","Pause effect"):tr("开启实时效果","Start live effect"),#selector(toggle))
        item(tr("当前位置归零","Recenter lid"),#selector(recenter))
        menu.addItem(.separator())
        let hint=NSMenuItem(title:hotkey.registered ? tr("快捷开关  ⌃⌥⌘D","Quick toggle  ⌃⌥⌘D"):tr("快捷键被占用，请使用此菜单","Shortcut unavailable; use this menu"),action:nil,keyEquivalent:"");hint.isEnabled=false;menu.addItem(hint)
        item(tr("退出折光","Quit Foldlight"),#selector(quit),"q")
        statusItem.menu=menu
    }
    @objc func toggle() { if enabled||starting { stop() } else { start() } }
    private func start() {
        guard !starting,!enabled,let screen=preferredScreen() else { return }
        screenPermission=CGPreflightScreenCaptureAccess()
        if !screenPermission {
            // The system controls this permission. No accessibility or input-monitoring access is needed.
            if !CGRequestScreenCaptureAccess() {
                message=tr("请在系统设置中允许“折光”的屏幕录制，然后再次点击开启。","Allow Foldlight in Screen Recording settings, then click Start again.")
                updateLabels();openPermissionSettings();return
            }
        }
        screenPermission=true
        generation+=1;let revision=generation;starting=true;message=tr("正在连接实时桌面…","Connecting to your desktop…")
        updateLabels();rebuildMenu()
        Task { @MainActor [self] in
            do {
                guard let device=MTLCreateSystemDefaultDevice() else { throw NSError(domain:"Metal",code:0,userInfo:[NSLocalizedDescriptionKey:"Metal unavailable"]) }
                let renderer=try DesktopRenderer(device:device);renderer.blur=blur;renderer.responseSeconds=highPrecision ? 0.018:0.06
                let overlay=OverlayWindow(screen:screen,renderer:renderer,fps:effectiveFPS(screen))
                let capture=DesktopCapture(mailbox:renderer.mailbox)
                self.renderer=renderer;self.overlay=overlay;self.capture=capture;self.selectedScreen=screen;firstFrame=false
                lastCaptureCount=0;lastRenderCount=0;lastCompletedCount=0;lastGPUTotal=0;lastMetricsTime=ProcessInfo.processInfo.systemUptime
                renderer.settled={ [weak self] in guard let self,self.generation==revision else { return };self.updateSettingsWindowLevel() }
                renderer.visibilityChanged={ [weak self] in guard let self,self.generation==revision else{return};self.updateSettingsWindowLevel() }
                renderer.failure={ [weak self] reason in guard let self,self.generation==revision else { return };self.fail(reason) }
                capture.onFailure={ [weak self] reason in guard let self,self.generation==revision else { return };self.fail(reason) }
                capture.onFirstFrame={ [weak self] in
                    guard let self,self.generation==revision else { return }
                    self.firstFrame=true;self.applyAngle()
                }
                try await capture.start(screen:screen,fps:effectiveFPS(screen))
                guard revision==generation else { await capture.stop();overlay.close();return }
                enabled=true;starting=false;defaults.set(true,forKey:"enabled")
                message=tr("已连接 · 合上屏幕即可体验。","Connected · Move the lid to see the effect.")
                applyAngle();updateLabels();rebuildMenu()
                DispatchQueue.main.asyncAfter(deadline:.now()+5) { [weak self] in
                    guard let self,self.generation==revision,self.enabled,!self.firstFrame else { return }
                    self.fail(self.tr("暂未收到桌面画面，请检查屏幕录制权限。","No desktop frames received. Check Screen Recording access."))
                }
            } catch {
                guard revision==generation else { return };fail(error.localizedDescription)
            }
        }
    }
    private func stop(persist: Bool=true) {
        generation+=1;enabled=false;starting=false;firstFrame=false
        overlay?.orderOut(nil);overlay?.metalView.isPaused=true;overlay?.close();overlay=nil
        renderer?.reset();renderer=nil;updateSettingsWindowLevel()
        let old=capture;capture=nil
        Task { await old?.stop() }
        if persist { defaults.set(false,forKey:"enabled") }
        message=tr("已暂停 · 桌面恢复原样。","Paused · Your original desktop is visible.")
        updateLabels();rebuildMenu()
    }
    private func fail(_ reason:String) { stop();message=tr("已停止效果：","Effect stopped: ")+reason;updateLabels() }
    private var effectAngle:Double {
        let value=manual ? manualAngle:raw.flatMap{r in reference.map{FoldMath.angle(raw:r,reference:$0)}} ?? 0
        return !manual && abs(value)<0.2 ? 0:value
    }
    private func applyAngle() {
        let angle=effectAngle
        renderer?.setTarget(angle,at:manual ? CACurrentMediaTime():sampleTime,buffered:!manual)
        guard enabled,firstFrame,let overlay,let renderer else { return }
        // The window starts clear and remains ordered while enabled. Visibility
        // follows the rendered angle's alpha, not noisy raw-angle window toggles.
        if !overlay.isVisible {overlay.orderFrontRegardless()}
        renderer.resume()
    }
    @objc func recenter() { renderer?.recenterMotion();reference=raw;manualAngle=0;angleSlider?.doubleValue=0;applyAngle();updateLabels() }
    private func suspend() { stop(persist:false);sensor.stop() }
    private func resume() { reference=nil;sensor.start();if defaults.bool(forKey:"enabled") { start() } }
    private func displayChanged() {
        let wasEnabled=enabled||starting
        if wasEnabled { stop(persist:false) }
        if let window,window.isVisible { showSettings() }
        if wasEnabled { start() }
    }
    @objc func quit() { stop(persist:false);NSApp.terminate(nil) }
    @objc func openPermissionSettings() { NSWorkspace.shared.open(URL(string:"x-apple.systempreferences:com.apple.preference.security?Privacy_ScreenCapture")!) }
    private func label(_ text:String,size:CGFloat=13,weight:NSFont.Weight = .regular)->NSTextField {
        let label=NSTextField(wrappingLabelWithString:text);label.font = .systemFont(ofSize:size,weight:weight);return label
    }
    private func button(_ text:String,_ action:Selector)->NSButton { let b=NSButton(title:text,target:self,action:action);b.bezelStyle = .rounded;return b }
    private func row(_ views:[NSView])->NSStackView {
        let row=NSStackView(views:views);row.orientation = .horizontal;row.spacing=12;row.alignment = .centerY;return row
    }
    @objc func showSettings() {
        screenPermission=CGPreflightScreenCaptureAccess()
        if window==nil {
            let window=NSWindow(contentRect:NSRect(x:0,y:0,width:780,height:630),styleMask:[.titled,.closable,.miniaturizable,.fullSizeContentView],backing:.buffered,defer:false)
            window.title=tr("折光","Foldlight");window.titleVisibility = .hidden;window.titlebarAppearsTransparent=true
            window.isMovableByWindowBackground=true;window.isReleasedWhenClosed=false
            window.level = .normal
            self.window=window;window.center()
        }
        renderSettings();window?.makeKeyAndOrderFront(nil);NSApp.activate(ignoringOtherApps:true);updateSettingsWindowLevel()
    }
    @objc func selectSettingsPage(_ sender:NSButton) {
        settingsPage=sender.tag;renderSettings()
        if !NSWorkspace.shared.accessibilityDisplayShouldReduceMotion,let content=window?.contentView {
            content.alphaValue=0.75;NSAnimationContext.runAnimationGroup { context in context.duration=0.12;content.animator().alphaValue=1 }
        }
    }
    private func renderSettings() {
        guard let window else{return}
        stateLabel=nil;startButton=nil;sensorLabel=nil;angleLabel=nil;angleSlider=nil;blurLabel=nil;metricsLabel=nil;precisionLabel=nil;hingePreview=nil;angleReadout=nil
        let root=SurfaceView();window.contentView=root
        let sidebar=NSVisualEffectView();sidebar.material = .sidebar;sidebar.blendingMode = .behindWindow;sidebar.state = .active
        let main=SurfaceView();root.addSubview(sidebar);root.addSubview(main)
        for view in [sidebar,main]{view.translatesAutoresizingMaskIntoConstraints=false}
        NSLayoutConstraint.activate([sidebar.leadingAnchor.constraint(equalTo:root.leadingAnchor),sidebar.topAnchor.constraint(equalTo:root.topAnchor),sidebar.bottomAnchor.constraint(equalTo:root.bottomAnchor),sidebar.widthAnchor.constraint(equalToConstant:182),main.leadingAnchor.constraint(equalTo:sidebar.trailingAnchor),main.trailingAnchor.constraint(equalTo:root.trailingAnchor),main.topAnchor.constraint(equalTo:root.topAnchor),main.bottomAnchor.constraint(equalTo:root.bottomAnchor)])
        let icon=NSImageView();icon.image=NSImage(contentsOf:Bundle.main.url(forResource:"AppIcon",withExtension:"icns")!);icon.widthAnchor.constraint(equalToConstant:34).isActive=true;icon.heightAnchor.constraint(equalToConstant:34).isActive=true
        let name=label(tr("折光","Foldlight"),size:19,weight:.semibold),sub=label("FOR MAC",size:9,weight:.medium);sub.textColor = .tertiaryLabelColor
        let identity=row([icon,layoutStack([name,sub],spacing:3)])
        let effects=NavigationButton(title:tr("  桌面效果","  Desktop effect"),symbol:"rectangle.on.rectangle.angled",target:self,action:#selector(selectSettingsPage(_:)));effects.tag=0;effects.selected=settingsPage==0
        let prefs=NavigationButton(title:tr("  偏好设置","  Preferences"),symbol:"slider.horizontal.3",target:self,action:#selector(selectSettingsPage(_:)));prefs.tag=1;prefs.selected=settingsPage==1
        let navigation=layoutStack([identity,effects,prefs],spacing:8);navigation.setCustomSpacing(30,after:identity)
        sidebar.addSubview(navigation);navigation.translatesAutoresizingMaskIntoConstraints=false
        NSLayoutConstraint.activate([navigation.leadingAnchor.constraint(equalTo:sidebar.leadingAnchor,constant:16),navigation.trailingAnchor.constraint(equalTo:sidebar.trailingAnchor,constant:-16),navigation.topAnchor.constraint(equalTo:sidebar.topAnchor,constant:56),effects.widthAnchor.constraint(equalTo:navigation.widthAnchor),prefs.widthAnchor.constraint(equalTo:navigation.widthAnchor)])
        sidebarStatus=label("",size:11,weight:.medium)
        let footnote=label("made by zk",size:10);footnote.textColor = .tertiaryLabelColor
        let footer=layoutStack([sidebarStatus!,footnote],spacing:8);sidebar.addSubview(footer);footer.translatesAutoresizingMaskIntoConstraints=false
        NSLayoutConstraint.activate([footer.leadingAnchor.constraint(equalTo:sidebar.leadingAnchor,constant:22),footer.bottomAnchor.constraint(equalTo:sidebar.bottomAnchor,constant:-25)])
        let stack=layoutStack([],spacing:17);main.addSubview(stack);stack.translatesAutoresizingMaskIntoConstraints=false
        NSLayoutConstraint.activate([stack.leadingAnchor.constraint(equalTo:main.leadingAnchor,constant:30),stack.trailingAnchor.constraint(equalTo:main.trailingAnchor,constant:-30),stack.topAnchor.constraint(equalTo:main.topAnchor,constant:48)])
        func add(_ view:NSView,fill:Bool=true){stack.addArrangedSubview(view);if fill{view.widthAnchor.constraint(equalTo:stack.widthAnchor).isActive=true}}
        if settingsPage==0 {
            startButton=button("",#selector(toggle));startButton?.controlSize = .large;startButton?.bezelColor = .controlAccentColor
            let heading=row([label(tr("桌面效果","Desktop effect"),size:24,weight:.semibold),flexibleSpace(),startButton!]);add(heading)
            stateLabel=label("",size:12);stateLabel?.textColor = .secondaryLabelColor;add(stateLabel!)
            let preview=HingePreviewView();preview.widthAnchor.constraint(equalToConstant:182).isActive=true;preview.heightAnchor.constraint(equalToConstant:123).isActive=true;hingePreview=preview
            let caption=label(tr("当前转动","CURRENT TILT"),size:10,weight:.medium);caption.textColor = .secondaryLabelColor
            angleReadout=label("0.00°",size:38,weight:.regular);angleReadout?.font = .monospacedDigitSystemFont(ofSize:38,weight:.regular)
            sensorLabel=label("",size:11);sensorLabel?.textColor = .secondaryLabelColor
            let readout=layoutStack([caption,angleReadout!,sensorLabel!],spacing:8)
            add(row([preview,readout,flexibleSpace()]));add(hairline())
            let mode=NSSegmentedControl(labels:[tr("合盖感应","Lid sensor"),tr("手动预览","Manual preview")],trackingMode:.selectOne,target:self,action:#selector(changeMode(_:)));mode.selectedSegment=manual ? 1:0;mode.controlSize = .large;modeControl=mode;add(mode)
            angleLabel=label(tr("转动角度","Tilt angle"),size:12,weight:.medium);add(angleLabel!)
            let angle=NSSlider(value:manualAngle,minValue:-120,maxValue:0,target:self,action:#selector(changeAngle(_:)));angle.isContinuous=true;angleSlider=angle;add(angle)
            let limits=row([label("−120°",size:10),flexibleSpace(),label(tr("0°  归零","0°  Rest"),size:10)]);add(limits);stack.setCustomSpacing(5,after:angle)
            blurLabel=label("",size:12,weight:.medium);add(blurLabel!)
            let blurSlider=NSSlider(value:blur*100,minValue:0,maxValue:100,target:self,action:#selector(changeBlur(_:)));blurSlider.isContinuous=true;add(blurSlider)
            add(hairline())
            let shortcut=label("⌃⌥⌘D",size:11,weight:.medium);shortcut.textColor = .secondaryLabelColor
            add(row([button(tr("当前位置归零","Recenter lid"),#selector(recenter)),flexibleSpace(),shortcut,label(tr("快速开关","Quick toggle"),size:11)]))
            let tip=label(tr("关闭此窗口后，效果仍会在菜单栏后台运行。","Close this window to keep running in the menu bar."),size:11);tip.textColor = .tertiaryLabelColor;add(tip)
        } else {
            add(label(tr("偏好设置","Preferences"),size:24,weight:.semibold))
            let subtitle=label(tr("显示、性能与启动方式。","Display, performance and startup."),size:12);subtitle.textColor = .secondaryLabelColor;add(subtitle)
            let display=NSPopUpButton();display.target=self;display.action=#selector(changeDisplay(_:));let current=preferredScreen()
            for (index,screen) in NSScreen.screens.enumerated(){display.addItem(withTitle:screen.localizedName);if screen==current{display.selectItem(at:index)}};displayPopup=display
            add(row([label(tr("显示器","Display"),weight:.medium),flexibleSpace(),display]));add(hairline())
            let refresh=NSPopUpButton();refresh.addItems(withTitles:["60 FPS","120 FPS"]);refresh.selectItem(at:fps==120 ? 1:0);refresh.target=self;refresh.action=#selector(changeFPS(_:));fpsPopup=refresh
            add(row([label(tr("目标刷新率","Target refresh rate"),weight:.medium),flexibleSpace(),refresh]))
            metricsLabel=label("",size:10);metricsLabel?.textColor = .secondaryLabelColor;add(metricsLabel!);add(hairline())
            precisionLabel=label("",size:12);add(precisionLabel!);add(hairline())
            let login=NSButton(checkboxWithTitle:tr("登录后在后台运行","Run in background at login"),target:self,action:#selector(changeLogin(_:)));login.state=SMAppService.mainApp.status == .enabled ? .on:.off;add(login)
            let language=NSPopUpButton();language.addItems(withTitles:["中文","English"]);language.selectItem(at:chinese ? 0:1);language.target=self;language.action=#selector(changeLanguage(_:))
            add(row([label(tr("语言","Language"),weight:.medium),flexibleSpace(),language]));add(hairline())
            add(row([label(tr("屏幕录制权限","Screen Recording"),weight:.medium),flexibleSpace(),button(tr("系统设置…","System Settings…"),#selector(openPermissionSettings))]))
            let privacy=label(tr("画面仅在本机处理，不保存、不上传。效果层可穿透点击；大角度时请暂停效果后进行精确操作。","Frames stay on your Mac, without saving or uploading. Clicks pass through; pause the effect for precise interaction at large angles."),size:11);privacy.textColor = .secondaryLabelColor;add(privacy)
            let appVersion=Bundle.main.object(forInfoDictionaryKey:"CFBundleShortVersionString") as? String ?? ""
            let version=label("Foldlight \(appVersion) · macOS",size:10);version.textColor = .tertiaryLabelColor;add(version)
        }
        updateLabels();updateMetrics()
    }
    private func updateMetrics() {
        let now=ProcessInfo.processInfo.systemUptime,dt=max(0.01,now-lastMetricsTime)
        let captured=renderer?.mailbox.count() ?? 0,stats=renderer?.statistics.snapshot() ?? (0,0,0.0)
        let rendered=stats.0,gpuMS=(stats.2-lastGPUTotal)/Double(max(1,stats.1-lastCompletedCount))*1000
        lastCompletedCount=stats.1;lastGPUTotal=stats.2
        let captureFPS=max(0,Double(captured-lastCaptureCount)/dt),renderFPS=max(0,Double(rendered-lastRenderCount)/dt)
        lastCaptureCount=captured;lastRenderCount=rendered;lastMetricsTime=now
        metricsLabel?.stringValue=enabled ? tr("实时捕获 ","Capture ")+String(format:"%.0f",captureFPS)+tr(" · 实际呈现 "," · Presented ")+String(format:"%.0f FPS · GPU %.1f ms",renderFPS,gpuMS):tr("归零时效果层透明并暂停渲染，静止桌面会减少捕获帧数。","At zero the overlay is transparent and rendering pauses. A static desktop produces fewer capture frames.")
    }
    private func updateLabels() {
        let ready=screenPermission ? tr("已就绪 · 开启后随合盖角度变化。","Ready · Enable the effect to follow your lid."):tr("需要屏幕录制权限，以在本机渲染桌面。","Allow Screen Recording to render your desktop locally.")
        stateLabel?.stringValue=message.isEmpty ? ready:message
        startButton?.title=enabled||starting ? tr("暂停实时效果","Pause live effect"):tr("开启实时效果","Start live effect")
        let rawText=raw.map{String(format:highPrecision ? "%.2f°":"%.0f°",$0)} ?? tr("未检测到可用传感器","Sensor unavailable")
        sensorLabel?.stringValue=tr("合盖角度：","Lid angle: ")+rawText
        let angle=effectAngle
        angleLabel?.stringValue=manual ? tr("转动角度","Tilt angle"):tr("转动角度 · 由合盖感应控制","Tilt angle · Controlled by the lid")
        angleReadout?.stringValue=String(format:"%.2f°",angle)
        hingePreview?.lidAngle=raw ?? 110
        sidebarStatus?.stringValue=enabled ? tr("●  效果已开启","●  Effect is on"):tr("○  效果已暂停","○  Effect is paused")
        sidebarStatus?.textColor=enabled ? .controlAccentColor:.secondaryLabelColor
        precisionLabel?.stringValue=highPrecision ? tr("合盖传感器 · 细角度读取（0.01°）","Lid sensor · 0.01° resolution"):tr("合盖传感器 · 兼容模式","Lid sensor · Compatibility mode")
        angleSlider?.isEnabled=manual;if !manual { angleSlider?.doubleValue=angle }
        blurLabel?.stringValue=tr("磨砂强度  ","Frost strength  ")+String(format:"%.0f%%",blur*100)
    }
    @objc func changeMode(_ control:NSSegmentedControl) { manual=control.selectedSegment==1;manualAngle=0;applyAngle();updateLabels() }
    @objc func changeAngle(_ slider:NSSlider) { manualAngle=slider.doubleValue;applyAngle();updateLabels() }
    @objc func changeBlur(_ slider:NSSlider) { blur=slider.doubleValue/100;updateLabels() }
    @objc func changeFPS(_ popup:NSPopUpButton) { fps=popup.indexOfSelectedItem==1 ? 120:60;if enabled { stop(persist:false);start() } }
    @objc func changeDisplay(_ popup:NSPopUpButton) {
        guard NSScreen.screens.indices.contains(popup.indexOfSelectedItem) else { return }
        defaults.set(NSScreen.screens[popup.indexOfSelectedItem].deviceDescription[NSDeviceDescriptionKey("NSScreenNumber")],forKey:"displayID")
        if enabled { stop(persist:false);start() }
    }
    @objc func changeLanguage(_ popup:NSPopUpButton) { chinese=popup.indexOfSelectedItem==0;rebuildMenu();renderSettings() }
    @objc func changeLogin(_ button:NSButton) {
        do { if button.state == .on { try SMAppService.mainApp.register() } else { try SMAppService.mainApp.unregister() } }
        catch { message=error.localizedDescription;button.state = .off;updateLabels() }
    }
    private func runBenchmark() {
        guard CGPreflightScreenCaptureAccess() else { print("BENCHMARK screen permission missing");return }
        if !enabled && !starting { start() }
        DispatchQueue.main.asyncAfter(deadline:.now()+3) { [weak self] in
            guard let self,self.enabled,let renderer=self.renderer else { print("BENCHMARK capture failed");NSApp.terminate(nil);return }
            self.manual=true;self.manualAngle = -15;self.modeControl?.selectedSegment=1;self.applyAngle();self.updateLabels()
            let moving=ProcessInfo.processInfo.arguments.contains("--moving-benchmark")
            let sensorCadence=ProcessInfo.processInfo.arguments.contains("--sensor-cadence-benchmark")
            let sweepStart=CACurrentMediaTime()
            let sweep=Timer(timeInterval:sensorCadence ? 0.1:1/120.0,repeats:true) { [weak self] _ in
                MainActor.assumeIsolated {
                    guard let self,moving else{return}
                    self.manualAngle = -20 + 15*sin((CACurrentMediaTime()-sweepStart)*1.5)
                    if sensorCadence {renderer.setTarget(self.manualAngle,at:CACurrentMediaTime(),buffered:true)} else {self.applyAngle()}
                }
            }
            if moving {RunLoop.main.add(sweep,forMode:.common)}
            let before=renderer.statistics.snapshot(),captureBefore=renderer.mailbox.count(),start=CACurrentMediaTime()
            DispatchQueue.main.asyncAfter(deadline:.now()+8) { [weak self] in
                guard let self else { return }
                sweep.invalidate()
                let dt=CACurrentMediaTime()-start,after=renderer.statistics.snapshot()
                var report:[String:Any]=["seconds":dt,"captureFPS":Double(renderer.mailbox.count()-captureBefore)/dt,
                    "presentedFPS":Double(after.0-before.0)/dt,"gpuMilliseconds":(after.2-before.2)/Double(max(1,after.1-before.1))*1000,
                    "requestedFPS":self.fps,"actualDisplayLimit":self.selectedScreen?.maximumFramesPerSecond ?? 0,
                    "lidAvailable":self.raw != nil,"screenCaptureAuthorized":true]
                for (key,value) in renderer.statistics.timingReport(){ report[key]=value }
                let data=try! JSONSerialization.data(withJSONObject:report,options:[.prettyPrinted,.sortedKeys])
                let args=ProcessInfo.processInfo.arguments
                if let i=args.firstIndex(of:"--benchmark-output"),args.indices.contains(i+1) { try? data.write(to:URL(fileURLWithPath:args[i+1])) }
                print(String(data:data,encoding:.utf8)!)
                self.manualAngle=0;self.applyAngle();NSApp.terminate(nil)
            }
        }
    }
    private func runTransitionBenchmark() {
        guard screenPermission else {print("TRANSITION permission missing");return}
        if !enabled && !starting {start()}
        DispatchQueue.main.asyncAfter(deadline:.now()+3) { [weak self] in
            guard let self,self.enabled,let renderer=self.renderer,let overlay=self.overlay else {NSApp.terminate(nil);return}
            self.manual=true;self.window?.orderOut(nil)
            let windowID=overlay.windowNumber
            var checks=[[String:Any]]()
            let commands:[(Double,Double)]=[(0,-12),(1,0),(2,-12),(3,0),(4,-0.18),(4.2,-0.32),(4.4,0),(5,-12),(6,0),(7,-12),(7.2,0),(7.208,-12),(8,0)]
            for (delay,angle) in commands {
                DispatchQueue.main.asyncAfter(deadline:.now()+delay) { [weak self] in self?.manualAngle=angle;self?.applyAngle() }
            }
            for delay in [1.7,3.7,4.8,6.8,8.7] {
                DispatchQueue.main.asyncAfter(deadline:.now()+delay) {
                    let opacity=renderer.statistics.presentedOpacity() ?? -1
                    let passed=opacity==0 && overlay.metalView.isPaused && !renderer.isEffectVisible && overlay.isVisible && overlay.windowNumber==windowID && !overlay.isOpaque && overlay.metalView.layer?.isOpaque==false
                    checks.append(["at":delay,"passed":passed,"presentedOpacity":opacity,"paused":overlay.metalView.isPaused,"sameOrderedWindow":overlay.isVisible && overlay.windowNumber==windowID])
                }
            }
            DispatchQueue.main.asyncAfter(deadline:.now()+7.8) {
                let passed=renderer.isEffectVisible && !overlay.metalView.isPaused && renderer.target == -12
                checks.append(["at":7.8,"passed":passed,"quickReversalActive":passed])
            }
            DispatchQueue.main.asyncAfter(deadline:.now()+9.2) {
                let report:[String:Any]=["checks":checks,"passed":checks.count==6 && checks.allSatisfy{($0["passed"] as? Bool)==true}]
                let data=try! JSONSerialization.data(withJSONObject:report,options:[.prettyPrinted,.sortedKeys])
                let args=ProcessInfo.processInfo.arguments
                if let i=args.firstIndex(of:"--benchmark-output"),args.indices.contains(i+1) {try? data.write(to:URL(fileURLWithPath:args[i+1]))}
                print(String(data:data,encoding:.utf8)!);NSApp.terminate(nil)
            }
        }
    }
    private func runSelfTest() {
        // Opt-in local diagnostics: counters and sensor values only, never screen pixels.
        DispatchQueue.main.asyncAfter(deadline:.now()+2) { [weak self] in
            guard let self else { return }
            print("SELFTEST hotkeyRegistered=\(self.hotkey.registered) enabled=\(self.enabled) capturedFrames=\(self.renderer?.mailbox.count() ?? 0) permission=\(CGPreflightScreenCaptureAccess()) fineSensor=\(self.highPrecision) lid=\(self.raw.map(String.init(describing:)) ?? "unavailable")")
        }
    }
}
