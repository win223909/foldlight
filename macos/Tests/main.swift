import Foundation
import Metal

func require(_ value: @autoclosure ()->Bool,_ message:String) {
    guard value() else { fputs("FAIL: \(message)\n",stderr);exit(1) }
}
require(FoldMath.angle(raw:80,reference:110)==(-30),"closing delta")
require(FoldMath.angle(raw:150,reference:110)==0,"opening never positive")
require(FoldMath.angle(raw:0,reference:150)==(-120),"120 degree limit")
require(FoldMath.angle(raw:.nan,reference:110)==0,"invalid data reveals desktop")
var slow=0.0,fast=0.0
for _ in 0..<60 { slow=FoldMath.smooth(slow,toward:-60,dt:1/60) }
for _ in 0..<120 { fast=FoldMath.smooth(fast,toward:-60,dt:1/120) }
require(abs(slow-fast)<0.03,"refresh-rate independent motion")
let pixels=FoldMath.pixelSize(width:3000,height:2000,scale:2)
require(pixels.0*pixels.1<=6_000_000,"capture allocation budget")
require(LidReport.coarse([1,127,0])==127,"legacy integer angle decoding")
require(LidReport.fine([7,119,49,0,0])==126.63,"fine angle hundredths")
require(LidReport.fine([7,0xff,0xff,0xff,0xff])==nil,"invalid fine report rejected")
require(LidReport.fine([1,119,49,0,0])==nil,"wrong report ID rejected")
require(LidReport.fine([7,0,0])==nil,"truncated fine report rejected")
require(LidReport.agrees(fine:126.63,coarse:127),"fine report agrees with coarse")
require(!LidReport.agrees(fine:10,coarse:127),"unrelated vendor data cannot drive effect")
// A 12 degrees/second slow lid sweep: fine input should avoid the one-degree
// stop/start acceleration of the legacy rounded input at the same draw cadence.
var coarseOutput=0.0,fineOutput=0.0,coarseSteps=[Double](),fineSteps=[Double]()
for tick in 0..<240 {
    let target = -Double(tick)/120*12
    let nextCoarse=FoldMath.smooth(coarseOutput,toward:target.rounded(),dt:1/120)
    let nextFine=FoldMath.smooth(fineOutput,toward:(target*100).rounded()/100,dt:1/120)
    if tick>30 {coarseSteps.append(nextCoarse-coarseOutput);fineSteps.append(nextFine-fineOutput)}
    coarseOutput=nextCoarse;fineOutput=nextFine
}
let coarseJerk=zip(coarseSteps.dropFirst(),coarseSteps).map{abs($0-$1)}.max()!
let fineJerk=zip(fineSteps.dropFirst(),fineSteps).map{abs($0-$1)}.max()!
require(fineJerk<coarseJerk*0.1,"fine motion removes quantized stop/start steps")
print("Motion replay maximum frame-step change: coarse=\(coarseJerk), fine=\(fineJerk)")
// Replay synthetic timestamped lid movements: the 120 Hz poll returned the same
// report for ~100 ms between hardware updates. Fine precision alone cannot fix it.
let tracePath=URL(fileURLWithPath:CommandLine.arguments[1]).deletingLastPathComponent().deletingLastPathComponent().appendingPathComponent("Tests/LidMotionTrace.json")
let trace=try JSONDecoder().decode([[Double]].self,from:Data(contentsOf:tracePath))
var timeline=LidMotionTimeline();timeline.reset(at:0)
var index=0,old=0.0,reconstructed=0.0,lastTarget=0.0
var oldSteps=[Double](),newSteps=[Double]()
for tick in 0..<480 {
    let t=Double(tick)/120
    while index<trace.count && trace[index][0]<=t {
        lastTarget=trace[index][1];timeline.append(lastTarget,at:trace[index][0]);index+=1
    }
    let nextOld=FoldMath.smooth(old,toward:lastTarget,dt:1/120,response:0.018)
    let nextNew=FoldMath.smooth(reconstructed,toward:timeline.value(at:t),dt:1/120,response:0.018)
    require((-120...0).contains(nextNew),"reconstruction remains within valid fold range")
    oldSteps.append(nextOld-old);newSteps.append(nextNew-reconstructed)
    old=nextOld;reconstructed=nextNew
}
func accelerationRMS(_ steps:[Double])->Double {
    sqrt(zip(steps,steps.dropFirst()).map{pow($1-$0,2)}.reduce(0,+)/Double(steps.count-1))
}
let oldRMS=accelerationRMS(oldSteps),newRMS=accelerationRMS(newSteps)
print("Synthetic lid replay frame acceleration RMS: old=\(oldRMS), buffered=\(newRMS)")
require(newRMS<oldRMS*0.4,"synthetic 10 Hz sensor trace loses stop/start jerk")
require(abs(reconstructed-lastTarget)<0.001,"stopped sensor settles without extrapolation drift")
var sparse=LidMotionTimeline();sparse.reset(at:0);sparse.append(-10,at:10)
require(sparse.value(at:9.99)==0,"long stationary gaps stay stationary before movement")
require(sparse.value(at:10.23)==(-10),"sparse report reaches measured angle after buffer delay")
var uniform=LidMotionTimeline();uniform.reset(at:0)
for tick in 1...10 {uniform.append(-Double(tick),at:Double(tick)/10)}
require(abs(uniform.value(at:0.765)+6.5)<0.0001,"interpolates intermediate angle between 10 Hz samples")
uniform.append(.nan,at:1.1);uniform.append(-100,at:0.1)
require(uniform.value(at:2)==(-10),"invalid and stale samples cannot rewind motion")
let device=MTLCreateSystemDefaultDevice()!
let shader=try String(contentsOfFile:CommandLine.arguments[1],encoding:.utf8)
let library=try device.makeLibrary(source:shader,options:nil)
let desc=MTLRenderPipelineDescriptor();desc.vertexFunction=library.makeFunction(name:"foldVertex");desc.fragmentFunction=library.makeFunction(name:"foldFragment");desc.colorAttachments[0].pixelFormat = .bgra8Unorm
let pipeline=try device.makeRenderPipelineState(descriptor:desc)
let w=320,h=180,textureDesc=MTLTextureDescriptor.texture2DDescriptor(pixelFormat:.bgra8Unorm,width:w,height:h,mipmapped:false)
textureDesc.storageMode = .shared;textureDesc.usage=[.shaderRead,.renderTarget]
let source=device.makeTexture(descriptor:textureDesc)!,output=device.makeTexture(descriptor:textureDesc)!
var image=[UInt8](repeating:255,count:w*h*4)
for y in 0..<h { for x in 0..<w {
    let i=(y*w+x)*4
    image[i]=UInt8(x%256);image[i+1]=UInt8(y%256);image[i+2]=((x/8+y/8)%2==0) ? 220:30
} }
source.replace(region:MTLRegionMake2D(0,0,w,h),mipmapLevel:0,withBytes:image,bytesPerRow:w*4)
let queue=device.makeCommandQueue()!
let glassBlur=try GlassBlur(device:device,library:library)
func render(_ angle:Float,_ blur:Float,_ opacity:Float=1)->[UInt8] {
    let pass=MTLRenderPassDescriptor();pass.colorAttachments[0].texture=output;pass.colorAttachments[0].loadAction = .clear;pass.colorAttachments[0].storeAction = .store
    let command=queue.makeCommandBuffer()!
    let glass=try! glassBlur.encode(source,command:command,refresh:true)
    let encoder=command.makeRenderCommandEncoder(descriptor:pass)!
    var u=[SIMD4<Float>(Float(w),Float(h),1,1),SIMD4<Float>(angle * .pi/180,blur,opacity,glassBlur.baseSigma)]
    encoder.setRenderPipelineState(pipeline);encoder.setFragmentTexture(source,index:0);encoder.setFragmentTexture(glass,index:1);encoder.setFragmentBytes(&u,length:32,index:0)
    encoder.drawPrimitives(type:.triangle,vertexStart:0,vertexCount:3);encoder.endEncoding();command.commit();command.waitUntilCompleted()
    require(command.status == .completed,"GPU command completes")
    var bytes=[UInt8](repeating:0,count:w*h*4);output.getBytes(&bytes,bytesPerRow:w*4,from:MTLRegionMake2D(0,0,w,h),mipmapLevel:0)
    return bytes
}
let identity=render(0,0.08)
require(zip(image,identity).allSatisfy{abs(Int($0)-Int($1))<=1},"zero angle preserves orientation and colors")
let clear=render(-40,0),frost=render(-40,0.08)
require(clear != image,"fold changes desktop projection")
require(clear != frost,"8 percent frost changes sampling")
let shut=render(-120,0.08)
require(stride(from:0,to:shut.count,by:4).allSatisfy{shut[$0]==0&&shut[$0+1]==0&&shut[$0+2]==0},"closed screen is dark")
print("PASS: geometry, 60/120 Hz motion, allocation budget, Metal compilation, identity colors, fold, frost and closed-screen rendering")

// Endpoint transparency must be real zero-alpha pixels, not an opaque black clear.
let transparent=render(0,0.08,0)
require(transparent.allSatisfy{$0==0},"rest drawable is fully transparent black")
let halfVisible=render(-0.5,0.08,0.5)
require(stride(from:3,to:halfVisible.count,by:4).allSatisfy{abs(Int(halfVisible[$0])-128)<=1},"partial visibility alpha covers all pixels including out-of-bounds")
require(stride(from:0,to:halfVisible.count,by:4).allSatisfy{i in (0..<3).allSatisfy{halfVisible[i+$0]<=halfVisible[i+3]}},"transition colors are premultiplied for the window compositor")
require(FoldMath.overlayOpacity(angle:0)==0,"zero is transparent")
for noise in [-0.34,-0.22,-0.1,0] {require(FoldMath.overlayOpacity(angle:noise)==0,"rest noise cannot flash the overlay")}
require(FoldMath.overlayOpacity(angle:-2.5)==1,"normal fold remains opaque")
var lastOpacity=0.0
for step in 0...250 {
 let opacity=FoldMath.overlayOpacity(angle:-Double(step)/100)
 require(opacity>=lastOpacity && opacity-lastOpacity<0.01,"endpoint fade has no discontinuity")
 lastOpacity=opacity
}
print("PASS: transparent rest, premultiplied transition pixels, near-zero noise and continuous opacity")

// A stationary point on the original desktop must remain on the same observer
// ray when the physical panel rotates. Solve the ray/lid intersection independently
// and verify that the GPU still returns that content point's source coordinates.
let fixedContent=SIMD2<Double>(128,75),eyeDistance=Double(h)*3,eyeHeight=Double(h)*0.5
for degrees in [10.0,25.0,50.0] {
    let a=degrees*Double.pi/180,contentHeight=Double(h)-fixedContent.y
    let lidDistance=contentHeight*eyeDistance/(eyeDistance*cos(a)+(contentHeight-eyeHeight)*sin(a))
    let depth=lidDistance*sin(a)
    let physicalX=Double(w)*0.5+(fixedContent.x-Double(w)*0.5)*(1-depth/eyeDistance)
    let physicalY=Double(h)-lidDistance
    let x=Int(physicalX.rounded()),y=Int(physicalY.rounded())
    require((0..<w).contains(x) && (0..<h).contains(y),"fixed content point remains on visible physical lid")
    let pixel=render(-Float(degrees),0),i=(y*w+x)*4
    require(abs(Double(pixel[i])-fixedContent.x)<=2 && abs(Double(pixel[i+1])-fixedContent.y)<=2,
            "fixed observer sees the same content coordinate at \(degrees) degrees")
}

let white=[UInt8](repeating:255,count:w*h*4)
source.replace(region:MTLRegionMake2D(0,0,w,h),mipmapLevel:0,withBytes:white,bytesPerRow:w*4)
let unfogged=render(-55,0.2),whiteIndex=(40*w+160)*4
require((0..<3).allSatisfy{unfogged[whiteIndex+$0]>=253},"defocus preserves constant light instead of adding angle-based black fog")
source.replace(region:MTLRegionMake2D(0,0,w,h),mipmapLevel:0,withBytes:image,bytesPerRow:w*4)
let probeDepth=Float(h-40)*sin(Float(35)*Float.pi/180)
let sigmaPerStrength=Float(h)*1.5*probeDepth/(Float(h)*3-probeDepth)
let kernelVariance:Float=0.92431216,boxVariance:Float=1/12
for mip in 0...3 {
    let sigma=sqrt(((kernelVariance+boxVariance)*pow(4,Float(mip))-boxVariance)/kernelVariance)
    let before=render(-35,sigma/sigmaPerStrength*0.9999)
    let after=render(-35,sigma/sigmaPerStrength*1.0001)
    let jump=zip(before,after).map{abs(Int($0)-Int($1))}.max()!
    require(jump<=3,"continuous Gaussian across fine/pyramid boundary \(mip), byte jump \(jump)")
}
var contrasts=[Double]()
for sigma:Float in [1,2,4,8] {
    let blurred=render(-35,sigma/sigmaPerStrength)
    let samples=(100..<220).map{Double(blurred[(40*w+$0)*4+2])}
    let mean=samples.reduce(0,+)/Double(samples.count)
    contrasts.append(sqrt(samples.map{pow($0-mean,2)}.reduce(0,+)/Double(samples.count)))
}
require(zip(contrasts,contrasts.dropFirst()).allSatisfy{$1<$0},"Gaussian blur progressively attenuates fine detail with focal distance")
print("PASS: fixed-observer content invariance, constant-light defocus, continuous Gaussian boundaries; contrast \(contrasts)")

// Benchmark live-frame Gaussian preparation and final projection at desktop resolution.
let largeDesc=MTLTextureDescriptor.texture2DDescriptor(pixelFormat:.bgra8Unorm,width:2560,height:1600,mipmapped:false)
largeDesc.storageMode = .shared;largeDesc.usage=[.shaderRead,.renderTarget]
let liveSource=device.makeTexture(descriptor:largeDesc)!,liveOutput=device.makeTexture(descriptor:largeDesc)!
var livePixels=[UInt8](repeating:255,count:2560*1600*4)
for y in 0..<1600 {for x in 0..<2560 {let i=(y*2560+x)*4;livePixels[i]=UInt8(x%256);livePixels[i+1]=UInt8(y%256);livePixels[i+2]=((x/16+y/16)%2==0) ? 220:30}}
liveSource.replace(region:MTLRegionMake2D(0,0,2560,1600),mipmapLevel:0,withBytes:livePixels,bytesPerRow:2560*4)
var timings=[Double]()
for frame in 0..<40 {
    let command=queue.makeCommandBuffer()!,glass=try glassBlur.encode(liveSource,command:command,refresh:true)
    let pass=MTLRenderPassDescriptor();pass.colorAttachments[0].texture=liveOutput;pass.colorAttachments[0].loadAction = .dontCare;pass.colorAttachments[0].storeAction = .store
    let encoder=command.makeRenderCommandEncoder(descriptor:pass)!
    var u=[SIMD4<Float>(2560,1600,1,1),SIMD4<Float>(-Float(frame+10) * .pi/180,0.2,1,glassBlur.baseSigma)]
    encoder.setRenderPipelineState(pipeline);encoder.setFragmentTexture(liveSource,index:0);encoder.setFragmentTexture(glass,index:1);encoder.setFragmentBytes(&u,length:32,index:0)
    encoder.drawPrimitives(type:.triangle,vertexStart:0,vertexCount:3);encoder.endEncoding();command.commit();command.waitUntilCompleted()
    require(command.status == .completed,"desktop resolution glass rendering")
    if frame>=5 {timings.append((command.gpuEndTime-command.gpuStartTime)*1000)}
}
print("Gaussian + fold 2560x1600 GPU average ms: \(timings.reduce(0,+)/Double(timings.count)); max ms: \(timings.max()!)")
