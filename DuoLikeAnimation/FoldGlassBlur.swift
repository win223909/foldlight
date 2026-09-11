import Metal

/// A reusable Gaussian pyramid. Live capture refreshes it only when a new frame arrives.
nonisolated final class FoldGlassBlur {
    private let device: MTLDevice
    private let downsample: MTLRenderPipelineState, gaussian: MTLRenderPipelineState
    private var base: MTLTexture?, glass: MTLTexture?
    private var scratch=[MTLTexture]()
    private var inputWidth=0,inputHeight=0
    private(set) var baseSigma:Float=4
    init(device:MTLDevice,library:MTLLibrary) throws {
        self.device=device
        func pipeline(_ fragment:String)throws->MTLRenderPipelineState {
            let desc=MTLRenderPipelineDescriptor();desc.vertexFunction=library.makeFunction(name:"foldTextureVertex")
            desc.fragmentFunction=library.makeFunction(name:fragment);desc.colorAttachments[0].pixelFormat = .rgba8Unorm_srgb
            return try device.makeRenderPipelineState(descriptor:desc)
        }
        downsample=try pipeline("foldGlassDownsample");gaussian=try pipeline("foldGlassGaussian")
    }
    func encode(_ source:MTLTexture,command:MTLCommandBuffer,refresh:Bool)throws->MTLTexture {
        let resized=source.width != inputWidth || source.height != inputHeight
        if resized {
            var w=source.width,h=source.height,divisor=1
            while max(w,h)>1024 {w=max(1,w/2);h=max(1,h/2);divisor*=2}
            baseSigma=4*Float(divisor)
            func texture(_ w:Int,_ h:Int,_ mip:Bool)throws->MTLTexture {
                let d=MTLTextureDescriptor.texture2DDescriptor(pixelFormat:.rgba8Unorm_srgb,width:w,height:h,mipmapped:mip)
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
        try draw(source,base,0,downsample,SIMD4(Float(base.width),Float(base.height),0,0))
        guard let blit=command.makeBlitCommandEncoder() else {throw NSError(domain:"GlassBlur",code:4)}
        blit.generateMipmaps(for:base);blit.endEncoding()
        for level in 0..<base.mipmapLevelCount {
            let temp=scratch[level]
            try draw(base,temp,0,gaussian,SIMD4(Float(temp.width),Float(temp.height),Float(level),0))
            try draw(temp,glass,level,gaussian,SIMD4(Float(temp.width),Float(temp.height),0,1))
        }
        return glass
    }
}
