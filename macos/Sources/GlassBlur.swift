import Metal

/// A reusable Gaussian pyramid. Live capture refreshes it only when a new frame arrives.
final class GlassBlur {
    private let device: MTLDevice
    private let gaussian: MTLRenderPipelineState
    private var base: MTLTexture?, glass: MTLTexture?
    private var scratch=[MTLTexture]()
    private var inputWidth=0,inputHeight=0
    private(set) var baseSigma:Float=1
    init(device:MTLDevice,library:MTLLibrary) throws {
        self.device=device
        func pipeline(_ fragment:String)throws->MTLRenderPipelineState {
            let desc=MTLRenderPipelineDescriptor();desc.vertexFunction=library.makeFunction(name:"foldVertex")
            desc.fragmentFunction=library.makeFunction(name:fragment);desc.colorAttachments[0].pixelFormat = .bgra8Unorm
            return try device.makeRenderPipelineState(descriptor:desc)
        }
        gaussian=try pipeline("glassGaussian")
    }
    func encode(_ source:MTLTexture,command:MTLCommandBuffer,refresh:Bool)throws->MTLTexture {
        let resized=source.width != inputWidth || source.height != inputHeight
        if resized {
            // Keep level zero at capture resolution. A reduced base resolution
            // discarded the small Gaussian kernels, leaving a clear/blur band.
            let w=source.width,h=source.height
            func texture(_ w:Int,_ h:Int,_ mip:Bool)throws->MTLTexture {
                let d=MTLTextureDescriptor.texture2DDescriptor(pixelFormat:.bgra8Unorm,width:w,height:h,mipmapped:mip)
                d.storageMode = .private;d.usage=[.shaderRead,.renderTarget]
                guard let t=device.makeTexture(descriptor:d) else {throw NSError(domain:"GlassBlur",code:1)};return t
            }
            base=try texture(w,h,true);glass=try texture(w,h,true);scratch=[]
            for level in 0..<base!.mipmapLevelCount {scratch.append(try texture(max(1,w>>level),max(1,h>>level),false))}
            inputWidth=source.width;inputHeight=source.height
        }
        guard let base,let glass else {throw NSError(domain:"GlassBlur",code:2)}
        if !refresh && !resized {return glass}
        func draw(_ input:MTLTexture,_ output:MTLTexture,_ level:Int,_ pipeline:MTLRenderPipelineState,_ settings:SIMD4<Float>)throws {
            let pass=MTLRenderPassDescriptor();pass.colorAttachments[0].texture=output;pass.colorAttachments[0].level=level
            pass.colorAttachments[0].loadAction = .dontCare;pass.colorAttachments[0].storeAction = .store
            guard let e=command.makeRenderCommandEncoder(descriptor:pass) else {throw NSError(domain:"GlassBlur",code:3)}
            var u=settings;e.setRenderPipelineState(pipeline);e.setFragmentTexture(input,index:0)
            e.setFragmentBytes(&u,length:16,index:0);e.drawPrimitives(type:.triangle,vertexStart:0,vertexCount:3);e.endEncoding()
        }
        guard let blit=command.makeBlitCommandEncoder() else {throw NSError(domain:"GlassBlur",code:4)}
        blit.copy(from:source,sourceSlice:0,sourceLevel:0,sourceOrigin:MTLOrigin(x:0,y:0,z:0),
                  sourceSize:MTLSize(width:source.width,height:source.height,depth:1),
                  to:base,destinationSlice:0,destinationLevel:0,destinationOrigin:MTLOrigin(x:0,y:0,z:0))
        blit.generateMipmaps(for:base);blit.endEncoding()
        for level in 0..<base.mipmapLevelCount {
            let temp=scratch[level]
            try draw(base,temp,0,gaussian,SIMD4(Float(temp.width),Float(temp.height),Float(level),0))
            try draw(temp,glass,level,gaussian,SIMD4(Float(temp.width),Float(temp.height),0,1))
        }
        return glass
    }
}
