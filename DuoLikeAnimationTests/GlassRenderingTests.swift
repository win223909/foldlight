import Metal
import Testing
import UIKit
@testable import DuoLikeAnimation

@MainActor
struct GlassRenderingTests {
    @Test func gaussianGlassPreservesColorsAndSoftensWithoutNoise() throws {
        let device=try #require(MTLCreateSystemDefaultDevice()),library=try #require(device.makeDefaultLibrary())
        let format=UIGraphicsImageRendererFormat();format.scale=1;format.opaque=true
        let image=UIGraphicsImageRenderer(size:CGSize(width:320,height:240),format:format).image { context in
            UIColor(red:100/255,green:140/255,blue:210/255,alpha:1).setFill();context.fill(CGRect(x:0,y:0,width:320,height:240))
            for x in stride(from:0,to:160,by:8){UIColor.white.setFill();context.fill(CGRect(x:x,y:0,width:4,height:240))}
        }
        let textures=try FoldTextureFactory.makeTexture(source:try #require(image.cgImage),orientation:.up,device:device)
        let desc=MTLRenderPipelineDescriptor();desc.vertexFunction=library.makeFunction(name:"foldTextureVertex");desc.fragmentFunction=library.makeFunction(name:"foldTextureFragment");desc.colorAttachments[0].pixelFormat = .rgba8Unorm_srgb
        let pipeline=try device.makeRenderPipelineState(descriptor:desc),queue=try #require(device.makeCommandQueue())
        let td=MTLTextureDescriptor.texture2DDescriptor(pixelFormat:.rgba8Unorm_srgb,width:320,height:240,mipmapped:false);td.storageMode = .shared;td.usage=[.shaderRead,.renderTarget]
        let output=try #require(device.makeTexture(descriptor:td))
        func render(_ degrees:Float,_ spread:Float)throws->[UInt8] {
            let command=try #require(queue.makeCommandBuffer()),pass=MTLRenderPassDescriptor()
            pass.colorAttachments[0].texture=output;pass.colorAttachments[0].loadAction = .clear;pass.colorAttachments[0].storeAction = .store
            let encoder=try #require(command.makeRenderCommandEncoder(descriptor:pass)),a=degrees * .pi/180
            var uniforms=[SIMD4<Float>(320,240,1,1),SIMD4<Float>(sin(abs(a)),cos(abs(a)),degrees>0 ? 320:0,1920),SIMD4<Float>(spread,0.015,1,textures.sigma),SIMD4<Float>(1/320,1/240,0,0)]
            encoder.setRenderPipelineState(pipeline);encoder.setFragmentTexture(textures.photo,index:0);encoder.setFragmentTexture(textures.glass,index:1);encoder.setFragmentBytes(&uniforms,length:64,index:0)
            encoder.drawPrimitives(type:.triangle,vertexStart:0,vertexCount:3);encoder.endEncoding();command.commit();command.waitUntilCompleted()
            #expect(command.status == .completed)
            var bytes=[UInt8](repeating:0,count:320*240*4);output.getBytes(&bytes,bytesPerRow:320*4,from:MTLRegionMake2D(0,0,320,240),mipmapLevel:0);return bytes
        }
        let flat=try render(0,0.12),pixel=(120*320+240)*4
        for (channel,value) in [100,140,210,255].enumerated(){#expect(abs(Int(flat[pixel+channel])-value)<=2)}
        let clear=try render(35,0),glass=try render(35,0.12)
        #expect(clear != glass)
        #expect(try render(35,0.12)==glass)
        var clearEdges=0,glassEdges=0
        for x in 20..<100 {let i=(120*320+x)*4;clearEdges+=abs(Int(clear[i])-Int(clear[i+4]));glassEdges+=abs(Int(glass[i])-Int(glass[i+4]))}
        #expect(glassEdges<clearEdges/3)
        for angle:Float in [-90,90] {let black=try render(angle,0.12);#expect(stride(from:0,to:black.count,by:4).allSatisfy{black[$0]==0 && black[$0+1]==0 && black[$0+2]==0 && black[$0+3]==255})}
    }
}
