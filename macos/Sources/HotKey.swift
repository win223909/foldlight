import Carbon

final class HotKey {
    private var reference: EventHotKeyRef?
    private var handler: EventHandlerRef?
    var action: (() -> Void)?
    private(set) var registered=false
    init() {
        var spec=EventTypeSpec(eventClass:OSType(kEventClassKeyboard),eventKind:UInt32(kEventHotKeyPressed))
        InstallEventHandler(GetApplicationEventTarget(),{ _,_,context in
            guard let context else { return noErr }
            let key=Unmanaged<HotKey>.fromOpaque(context).takeUnretainedValue()
            DispatchQueue.main.async { key.action?() };return noErr
        },1,&spec,Unmanaged.passUnretained(self).toOpaque(),&handler)
        let id=EventHotKeyID(signature:0x44554F46,id:1)
        registered = RegisterEventHotKey(UInt32(kVK_ANSI_D),UInt32(controlKey|cmdKey|optionKey),id,GetApplicationEventTarget(),0,&reference) == noErr
    }
    deinit { if let reference { UnregisterEventHotKey(reference) };if let handler { RemoveEventHandler(handler) } }
}
