import {downloadCopy} from './download-copy.js';
import {drawDesktop} from './desktop-scene.js';
import {desktopScreens,phoneScreens} from './screen-assets.js';
import {installPageMotion} from './page-motion.js';
import {clampPreviewAngle,scrollPreviewAngle} from './presentation.js';
import {referenceFragment} from './reference-glass.js';
import {previewFragment} from './desktop-glass.js';
import {DesktopBlur} from './desktop-blur.js';
import {desktopOptics} from './desktop-optics.js';
import {captureDesktopFallback,renderDesktopFallback} from './desktop-fallback.js';
import {effectDefaults,nextBrightness} from './effect-settings.js';
import {GlassBlur} from './glass-blur.js';
import {screenLayout,isPhoneScreenshot} from './screen-layout.js';
import {smoothAngle} from './animation.js';
import {deviceKind,motionValue} from './capabilities.js';
const $=selector=>document.querySelector(selector),$$=selector=>[...document.querySelectorAll(selector)];
const text={zh:{brand:'折光',about:'关于体验',eyebrow:'一场随手开启的视觉体验',headline1:'轻转手机。',headline2:'看见不同。',intro:'让熟悉的画面，在角度之间变得不一样。',motion:'开启手机感应',motionOff:'关闭手机感应',motionWaiting:'等待手机感应…',idle:'无需安装 · 点击后授权',desktopHint:'请用手机打开体验；也可在这里左右拖动。',active:'感应已开启 · 轻轻转动手机',waiting:'保持当前姿态，正在等待感应数据。',denied:'未获得感应权限。可在浏览器设置中允许后重试，或左右拖动体验。',missing:'暂未收到感应数据。请使用支持姿态感应的手机浏览器，或左右拖动体验。',secure:'感应需要安全连接，请使用 HTTPS 地址。',simulated:'模拟桌面',canvas:'模拟手机桌面。左右拖动，或开启手机感应调整角度。',restore:'显示操作界面',wallpaper:'更换壁纸',fullscreen:'全屏体验',dragHint:'也可以左右拖动，先感受一下。',controls:'效果调节',adjustTitle:'刚刚好的感觉，由你调节。',reset:'重新归零 ↺',angle:'转动角度',blur:'磨砂强度',clear:'清晰',soft:'柔和',hide:'隐藏界面，只留下画面 ↗',privacy:'你的壁纸，只留在你的设备。无需上传。',personalize:'你的画面，你的风格。',close:'关闭',coast:'海岸晨光',dune:'沙丘日落',night:'蓝色时刻',upload:'选择照片或主屏幕截图',private:'照片或截图会作为完整画面显示。仅在本机处理，刷新后恢复默认。',aboutTitle:'关于折光',aboutText:'这是一个手机视觉体验。默认使用真实 示例主屏幕示例图片，随转动呈现透视、磨砂与光影。上传照片或主屏幕截图会直接显示该图片；内置壁纸带有模拟图标。不会读取你的真实桌面。',permissionTitle:'感应与权限',permissionText:'点击开启后，浏览器可能请求运动与方向权限。根据设备和浏览器能力启用；拒绝权限或没有传感器数据时，可继续拖动体验。',fullTitle:'更沉浸一点',fullText:'支持全屏的浏览器会进入系统全屏；其他浏览器会隐藏页面控件。轻点画面可恢复，iPhone 也可添加到主屏幕后打开。',fullFallback:'当前浏览器不支持系统全屏，已进入网页内沉浸模式。',imageError:'图片无法读取，请试试 JPG、PNG 或 WebP。',imageLarge:'请选择小于 25 MB 的图片。',imageBusy:'正在准备你的壁纸…',imageOK:'壁纸已更换，仅保留在当前网页。',gpuFallback:'当前浏览器使用简化效果，仍可拖动体验。',calibrated:'已将当前姿态归零。',weather:'晴朗',apps:['信息','照片','日历','时钟','地图','相机','备忘录','浏览器','邮件','音乐','文件','设置']},en:{brand:'Duo',about:'About',eyebrow:'A little shift in perspective',headline1:'A simple tilt.',headline2:'A different view.',intro:'See your familiar screen in a whole new light.',motion:'Enable phone motion',motionOff:'Turn motion off',motionWaiting:'Connecting motion…',idle:'No app needed · Permission on tap',desktopHint:'Open on your phone for motion, or drag here to explore.',active:'Motion is on · Gently tilt your phone',waiting:'Hold your phone steady. Waiting for motion data.',denied:'Motion access was denied. Allow it in browser settings and retry, or drag to explore.',missing:'No motion data received. Try a phone browser with motion support, or drag to explore.',secure:'Motion requires a secure connection. Open the HTTPS address.',simulated:'SIMULATED SCREEN',canvas:'Simulated phone screen. Drag sideways or enable motion to change the angle.',restore:'Show controls',wallpaper:'Wallpaper',fullscreen:'Fullscreen',dragHint:'Or drag sideways to get a feel for it.',controls:'Adjust the effect',adjustTitle:'Find your kind of subtle.',reset:'Recenter ↺',angle:'Tilt angle',blur:'Frosted glass',clear:'Clear',soft:'Soft',hide:'Hide controls. Keep the view. ↗',privacy:'Your wallpaper stays on your device. Never uploaded.',personalize:'Your screen. Your style.',close:'Close',coast:'Morning coast',dune:'Desert dusk',night:'Blue hour',upload:'Choose a photo or screenshot',private:'Your photo or screenshot fills the view. Processed on your device only. Reloading restores the default.',aboutTitle:'About Duo',aboutText:'A visual experience for your phone. The default image is an original illustrated demo screen. Explore perspective, frosted glass and light as you tilt. Uploaded photos and screenshots are shown as complete images. Built-in wallpapers include simulated icons. It never reads your actual home screen.',permissionTitle:'Motion & permission',permissionText:'Your browser may ask for motion and orientation access when you tap Enable. Support depends on the device and browser. Dragging remains available if permission is denied or sensor data is unavailable.',fullTitle:'A little more immersive',fullText:'Supported browsers enter system fullscreen. Otherwise, controls are hidden within the page. Tap the screen to restore them. On iPhone, you can also use Add to Home Screen.',fullFallback:'System fullscreen is unavailable. Using in-page immersive mode.',imageError:'Unable to read this photo. Try JPG, PNG or WebP.',imageLarge:'Please choose a photo smaller than 25 MB.',imageBusy:'Preparing your wallpaper…',imageOK:'Wallpaper changed. Kept only in this page.',gpuFallback:'Using simplified effects. You can still drag to explore.',calibrated:'Your current position is now zero.',weather:'Sunny',apps:['Messages','Photos','Calendar','Clock','Maps','Camera','Notes','Browser','Mail','Music','Files','Settings']}};
Object.assign(text.zh,{pageTitle:'折光 — Duo',pageDescription:'折光。开启手机感应，体验随角度变化的透视与磨砂。无需安装，图片仅在本机处理。'});
Object.assign(text.en,{pageTitle:'Duo — Foldlight',pageDescription:'Enable phone motion to explore perspective and frosted glass as you tilt. No app needed. Your images stay on your device.'});
Object.assign(text.zh,{macNav:'Mac 版',macTitle1:'合上屏幕。',macTitle2:'桌面随之转动。',macIntro:'在菜单栏安静运行，为实时桌面带来合盖效果。',macDownload:'下载 Mac 测试版',macBeta:'测试版 · 尚未经过 Apple 公证，首次打开可能需要在系统设置中确认。',macHelp:'安装与首次使用',macStep1:'打开下载的 DMG，将 Foldlight 拖入 Applications，再从“应用程序”打开。',macStep2:'若系统阻止打开，确认下载来源后，前往“系统设置 → 隐私与安全性”查看“仍要打开”，按提示确认。',macAppleHelp:'Apple 说明 ↗',macStep3:'点击“开启实时效果”，允许屏幕录制；若系统要求，请重新打开 App。',macStep4:'保持日常屏幕角度，点“当前位置归零”。⌃⌥⌘D 可随时暂停或开启。',macCompatibility:'合盖感应取决于设备是否提供可用传感器；不支持的型号仍可手动预览。已在 Mac14,6 / macOS 27 上实测，其他组合尚未完成实机验证。',macPrivacy:'桌面画面仅在本机处理，不保存、不上传。大角度下建议先暂停，再进行精确点击。',macReadme:'完整使用说明',macChecksum:'SHA-256 校验值'});
Object.assign(text.en,{macNav:'Mac app',macTitle1:'Close the lid.',macTitle2:'Shift your desktop.',macIntro:'A live desktop effect, quietly running in your menu bar.',macDownload:'Download for Mac · Beta',macBeta:'Beta · Not notarized by Apple. First launch may need confirmation in System Settings.',macHelp:'Install & get started',macStep1:'Open the DMG, drag Foldlight into Applications, then open it from Applications.',macStep2:'If macOS blocks it, verify the download source, then review Open Anyway in System Settings → Privacy & Security. Follow the system prompts.',macAppleHelp:'Apple guide ↗',macStep3:'Click Start live effect and allow Screen Recording. Relaunch the app if macOS asks.',macStep4:'Hold your lid at its normal angle and click Recenter lid. Use ⌃⌥⌘D to pause or enable the effect.',macCompatibility:'Lid sensing requires an available hardware sensor. Manual preview remains available otherwise. Physically tested on Mac14,6 / macOS 27; other combinations are not yet verified.',macPrivacy:'Desktop frames stay on your Mac, without saving or uploading. Pause the effect for precise clicks at large angles.',macReadme:'Full instructions',macChecksum:'SHA-256 checksum'});
Object.assign(text.zh,{compression:'边缘压缩程度',defaults:'恢复默认参数',defaultsRestored:'已恢复默认推荐参数',foldControls:'六项调节：外屏展开变暗 / 折叠变亮、内屏左侧展开变亮 / 折叠变暗的起始角度，外屏右侧压缩程度和模糊程度。推荐值依次为 80°、90°、55°、70°、60%、100%，可一键恢复。内屏右半边保持清晰、静止。'});
Object.assign(text.en,{compression:'Edge compression',defaults:'Restore defaults',defaultsRestored:'Recommended settings restored',foldControls:'Six controls: opening and closing brightness start angles for the cover and inner left, cover right-edge compression, and blur. Recommended values: 80°, 90°, 55°, 70°, 60%, 100%. Restore them with one tap. The inner right half remains clear and stationary.'});
Object.assign(text.zh,{heroBrand:'折光',experienceNav:'体验',downloadNav:'下载',about:'关于',phoneLine:'让视角，自然流动',desktopLine:'为桌面，添一层光',phoneDescription:'轻转手机，让熟悉的画面多一层光影。',desktopDescription:'透视、磨砂与光影，让熟悉的桌面有了新的视角。',playEffect:'播放效果',getMac:'获取 Mac 版',previewLabel:'交互效果预览',viewMode:'选择预览设备',phoneView:'手机',desktopView:'电脑',tune:'调节',wallpaper:'壁纸',fullscreen:'全屏',adjustTitle:'调到你喜欢。',reset:'重新归零',hide:'隐藏界面，只留下画面',desktopHint:'向下滚动至电脑居中，再看它慢慢合上。也可向下拖动，松手即停。',phoneHint:'左右拖动画面，也可以开启手机感应。',desktopNote:'MacBook Pro 16 英寸 · 银色 · 模拟桌面预览',phoneDesktopNote:'手机效果预览 · 手机浏览器可开启感应',desktopCanvas:'模拟电脑桌面。上下拖动调整角度。',desktopScreen:'桌面预览',desktopDefault:'恢复默认桌面',desktopAngle:'合盖预览角度',scrollHint:'向下，发现更多',storyEyebrow:'一点变化，很多感受。',storyTitle1:'清晰。柔和。',storyTitle2:'自然过渡。',feature1Title:'光影，跟着你走。',feature1Phone:'轻轻转动手机，透视与磨砂随着角度自然变化。',feature1Desktop:'在这里拖动预览。下载 Mac App，让光影随真实合盖变化。',feature2Title:'留下你喜欢的画面。',feature2Copy:'选择自己的照片或桌面截图。换一张图，就换一种感受。',feature3Title:'让画面，占满视野。',feature3Copy:'进入全屏，收起所有控件。轻点画面，随时回到这里。',nativeEyebrow:'把折光，带在身边。',nativeTitle:'Mac 版',nativeCopy:'后台运行，全局生效，让桌面和各个 App 的实时画面随合盖变化。',pageDescription:'折光 Duo。为手机与电脑呈现自然流动的透视、磨砂与光影。',aboutText:'折光是一个关于透视与磨砂的视觉实验。网页会根据设备展示手机或电脑预览，也可以手动切换。上传的图片会完整显示，并且仅在本机处理。网页不读取真实桌面；实时桌面与合盖感应由 Mac App 提供。'});
Object.assign(text.en,{heroBrand:'Duo',experienceNav:'Experience',downloadNav:'Download',about:'About',phoneLine:'A new point of view',desktopLine:'Your desktop. In a new light',phoneDescription:'A gentle tilt. A little perspective. A whole new feeling.',desktopDescription:'Perspective, frosted glass and light. A new dimension for your desktop.',playEffect:'Play the effect',getMac:'Get the Mac app',previewLabel:'Interactive effect preview',viewMode:'Choose a preview device',phoneView:'Phone',desktopView:'Desktop',tune:'Adjust',wallpaper:'Wallpaper',fullscreen:'Fullscreen',adjustTitle:'Make it feel like you.',reset:'Recenter',hide:'Hide controls. Keep the view.',desktopHint:'Scroll until the Mac is centered, then watch it close. Or drag down and release to pause.',phoneHint:'Drag sideways, or enable motion on your phone.',desktopNote:'16-inch MacBook Pro · Silver · Simulated desktop',phoneDesktopNote:'Phone preview · Enable motion in a supported phone browser',desktopCanvas:'Simulated desktop. Drag vertically to adjust the angle.',desktopScreen:'DESKTOP PREVIEW',desktopDefault:'Restore the default desktop',desktopAngle:'Lid preview angle',scrollHint:'Scroll to discover',storyEyebrow:'A LITTLE SHIFT. A DIFFERENT FEELING.',storyTitle1:'From clear to soft.',storyTitle2:'Naturally.',feature1Title:'Light follows your lead.',feature1Phone:'Gently tilt your phone. Perspective and frosted glass follow your movement.',feature1Desktop:'Drag to preview here. Get the Mac app to let light follow your lid.',feature2Title:'A view that feels like yours.',feature2Copy:'Choose a photo or a screenshot. A different image, a different feeling.',feature3Title:'Just you and the view.',feature3Copy:'Go fullscreen and let the controls disappear. Tap the screen to come back.',nativeEyebrow:'TAKE DUO WITH YOU.',nativeTitle:'Mac edition',nativeCopy:'Runs in the background, bringing a system-wide folding effect to your live desktop and apps.',pageDescription:'Duo. Flowing perspective, frosted glass and light, on your phone and desktop.',aboutText:'Duo is an experiment in perspective and frosted glass. Your device chooses the initial phone or desktop preview, and you can switch views anytime. Uploaded images are displayed directly and stay on your device. The website does not capture your desktop. Live desktop and lid sensing are provided by the Mac app.'});
Object.assign(text.zh,downloadCopy.zh);
Object.assign(text.en,downloadCopy.en);
const physicalDevice=deviceKind(navigator);
let viewMode=physicalDevice,userInteracted=false,demoFrame=0,demoRevision=0;
document.documentElement.dataset.view=viewMode;
let lang=navigator.language.startsWith('zh')?'zh':'en',blurStrength=effectDefaults.blur,compression=effectDefaults.compression,brightnessLevel=NaN;
try{const saved=localStorage.getItem('foldlight-language');if(saved==='zh'||saved==='en')lang=saved;const edge=localStorage.getItem('foldlight-compression');if(edge!==null&&Number.isFinite(Number(edge)))compression=Math.max(0,Math.min(1,Number(edge)));const blur=localStorage.getItem('foldlight-blur');if(blur!==null&&Number.isFinite(Number(blur)))blurStrength=Math.max(0,Math.min(1,Number(blur)))}catch{}
Object.assign(text.zh,{customScreen:'自定义图片',realScreen:'示例主屏幕',defaultScreen:'恢复默认 示例主屏幕',imageSource:'公开源码使用 Foldlight 自制矢量示例，不包含个人桌面截图。示例随项目以 MIT 许可提供。',sourceLink:'查看图片来源 ↗'});
Object.assign(text.en,{customScreen:'YOUR IMAGE',realScreen:'DEMO HOME SCREEN',defaultScreen:'Restore the demo screen',imageSource:'The source project uses an original Foldlight vector illustration under the MIT license, without personal or third-party screenshots.',sourceLink:'View image source ↗'});
Object.assign(text.zh,{guideLink:'如何全屏体验',guideEyebrow:'让画面占满视野',guideTitle:'全屏体验指南',guideIphone:'iPhone · 隐藏浏览器地址栏',guideContext:'推荐从主屏幕打开，可去掉 Safari 地址栏。',guideOther:'点击下方按钮进入全屏；不支持时，会切换为网页内沉浸。iPhone 用户可按以下步骤隐藏地址栏。',guideAndroid:'点击下方按钮进入全屏。若 App 内的浏览器不支持，可改用 Chrome 等独立浏览器打开。',guideStandalone:'你已从主屏幕打开。开启手机感应后，点下方按钮即可隐藏本站控件。',guideStep1:'用 Safari 打开折光',guideStep1Text:'如果在微信等 App 内，请先选择“在 Safari 中打开”。',guideStep2:'分享 → 添加到主屏幕',guideStep2Text:'点方框向上箭头的“分享”；部分版本需先点“…”再点“分享”。',guideStep3:'确认添加',guideStep3Text:'若出现“作为网页 App 打开”，保持开启，再点“添加”。',guideStep4:'从桌面图标重新打开',guideStep4Text:'开启手机感应，再点“全屏体验”。无需从 App Store 下载。',guideMissing:'找不到“添加到主屏幕”？',guideMissingText:'向下滚动分享菜单。若仍没有，点“编辑操作”把它加入菜单。',guideExitTitle:'随时回到操作界面',guideExit:'轻点画面即可返回；电脑也可按 Esc。',guidePreview:'先体验网页内沉浸',guidePreviewNote:'网页内沉浸会隐藏本站控件；浏览器地址栏可能仍然保留。',guideStart:'开始全屏体验'});
Object.assign(text.en,{guideLink:'How to go fullscreen',guideEyebrow:'Make room for the view',guideTitle:'Your fullscreen guide',guideIphone:'iPhone · Hide the address bar',guideContext:'For a view without Safari’s address bar, open Duo from your Home Screen.',guideOther:'Use the button below to enter fullscreen, with an immersive fallback if unavailable. On iPhone, follow these steps to hide the address bar.',guideAndroid:'Use the button below to enter fullscreen. If an in-app browser cannot do this, open the site in a browser such as Chrome.',guideStandalone:'You’re already using the Home Screen version. Enable phone motion, then use the button below to hide the controls.',guideStep1:'Open Duo in Safari',guideStep1Text:'In an app such as WeChat? Choose Open in Safari first.',guideStep2:'Share → Add to Home Screen',guideStep2Text:'Look for the square with an upward arrow. Some versions put Share inside the … menu.',guideStep3:'Confirm and add',guideStep3Text:'If Open as Web App appears, leave it on, then tap Add.',guideStep4:'Reopen from the Home Screen icon',guideStep4Text:'Enable phone motion, then tap Fullscreen. No App Store download needed.',guideMissing:'Can’t find Add to Home Screen?',guideMissingText:'Scroll down the Share menu. If it is missing, use Edit Actions to add it.',guideExitTitle:'Return whenever you like',guideExit:'Tap the screen to return. On a computer, you can also press Esc.',guidePreview:'Try immersive view now',guidePreviewNote:'This hides Duo’s controls. Your browser’s address bar may remain visible.',guideStart:'Start fullscreen'});
Object.assign(text.zh,{statusFit:'图片包含手机状态栏',statusFitNote:'从主屏幕全屏打开时，隐藏图片里的时间和电量，避免与系统状态栏重复。普通照片请关闭。'});
Object.assign(text.en,{statusFit:'Image includes a phone status bar',statusFitNote:'In the Home Screen fullscreen view, hide the image’s time and battery to avoid duplicating the system status bar. Turn off for ordinary photos.'});
const appleTouch=/iPhone|iPad|iPod/.test(navigator.userAgent)||(navigator.platform==='MacIntel'&&navigator.maxTouchPoints>1);
const standalone=()=>navigator.standalone===true||matchMedia('(display-mode: standalone)').matches||matchMedia('(display-mode: fullscreen)').matches;
function updateFullscreenGuide(){
 const installed=standalone(),android=/Android/i.test(navigator.userAgent);
 $('#guide-context').textContent=t(installed?'guideStandalone':appleTouch?'guideContext':android?'guideAndroid':'guideOther');
 $('#guide-iphone').hidden=installed||!appleTouch;
 $('#guide-start').textContent=t(appleTouch&&!installed?'guidePreview':'guideStart');
 $('#guide-preview-note').hidden=installed;
}
function showFullscreenGuide(){updateFullscreenGuide();$('#fullscreen-dialog').showModal()}
const t=key=>text[lang][key],stage=$('#stage'),source=$('#scene'),surface=$('#effect'),c=source.getContext('2d'),motionPreference=matchMedia('(prefers-reduced-motion:reduce)');
let reduceMotion=motionPreference.matches;
Object.assign(text.zh,{screenLoading:'正在加载主屏幕…',screenRetry:'主屏幕未加载，点此重试'});
Object.assign(text.en,{screenLoading:'Loading Home Screen…',screenRetry:'Home Screen unavailable. Tap to retry'});
let screenLoadFailed=false;
let useDefaultScreen=true,defaultImages={},defaultLoading={};
let imageHasStatus=true;
const safeProbe=document.createElement('div');safeProbe.className='safe-probe';document.body.append(safeProbe);
let wall='coast',photo=null,angle=0,target=0,raf=0,lastFrame=0,renderWidth=1,renderHeight=1,uploadRevision=0,toastTimer=0;
let motionEnabled=false,motionReceived=false,motionReference=null,lastOrientation=null,motionLastAt=0,motionTimer=0,motionRequest=0,statusKey=deviceKind(navigator)==='phone'?'idle':'desktopHint';
if(!CanvasRenderingContext2D.prototype.roundRect)CanvasRenderingContext2D.prototype.roundRect=function(x,y,w,h,r){r=Math.min(Number(r)||0,w/2,h/2);this.moveTo(x+r,y);this.arcTo(x+w,y,x+w,y+h,r);this.arcTo(x+w,y+h,x,y+h,r);this.arcTo(x,y+h,x,y,r);this.arcTo(x,y,x+w,y,r);this.closePath();return this};
$$('dialog').forEach(dialog=>{if(!dialog.showModal){dialog.showModal=()=>{dialog.setAttribute('open','');dialog.classList.add('dialog-fallback')};dialog.close=()=>{dialog.removeAttribute('open');dialog.classList.remove('dialog-fallback')}}});
function toast(key){$('#toast').textContent=t(key);$('#toast').classList.add('visible');clearTimeout(toastTimer);toastTimer=setTimeout(()=>$('#toast').classList.remove('visible'),4500)}
function updateMotionUI(){const key=physicalDevice==='desktop'&&viewMode==='phone'?'playEffect':motionEnabled?(motionReceived?'motionOff':'motionWaiting'):'motion';$('#motion-label').textContent=t(key);$('#motion').setAttribute('aria-pressed',String(motionEnabled));$('#motion-status').textContent=t(viewMode==='desktop'?'desktopHint':physicalDevice==='desktop'?'phoneDesktopNote':statusKey);$('#motion-status').classList.toggle('active',motionReceived);}
function roundRect(ctx,x,y,w,h,r,fill){ctx.beginPath();ctx.roundRect(x,y,w,h,r);if(fill){ctx.fillStyle=fill;ctx.fill()}}
function label(ctx,s,x,y,size,color='#fff',align='left',weight=400){ctx.fillStyle=color;ctx.textAlign=align;ctx.font=`${weight} ${size}px -apple-system,BlinkMacSystemFont,"Segoe UI","PingFang SC",sans-serif`;ctx.fillText(s,x,y)}
function wallpaper(ctx,w,h,theme){const palettes={coast:['#e6d9b7','#d8a98d','#416a6b','#153e49','#11323b'],dune:['#fae5c0','#d19881','#c77961','#8c514b','#412d38'],night:['#9ebec8','#7e96b7','#3c567e','#283955','#102538']};const p=palettes[theme];let g=ctx.createLinearGradient(0,0,w*.5,h);g.addColorStop(0,p[0]);g.addColorStop(.6,p[1]);g.addColorStop(1,p[2]);ctx.fillStyle=g;ctx.fillRect(0,0,w,h);const sx=w*.66,sy=h*.28,sr=Math.min(w,h)*.12;g=ctx.createRadialGradient(sx,sy,0,sx,sy,sr*2.8);g.addColorStop(0,theme==='night'?'#e5efff80':'#fff3d970');g.addColorStop(1,'#fff0');ctx.fillStyle=g;ctx.fillRect(0,0,w,h);ctx.beginPath();ctx.arc(sx,sy,sr,0,Math.PI*2);ctx.fillStyle=theme==='night'?'#eff7ed':'#fff1cf';ctx.fill();for(let layer=0;layer<4;layer++){const base=h*(.51+layer*.115);ctx.beginPath();ctx.moveTo(0,h);ctx.lineTo(0,base+h*.09);ctx.bezierCurveTo(w*.19,base-h*.18,w*.3,base+h*.22,w*.57,base-h*.02);ctx.bezierCurveTo(w*.75,base-h*.16,w*.86,base-h*.06,w,base+h*.02);ctx.lineTo(w,h);ctx.closePath();g=ctx.createLinearGradient(0,base-h*.1,w,h);g.addColorStop(0,p[Math.min(layer+1,4)]);g.addColorStop(1,p[Math.min(layer+2,4)]);ctx.fillStyle=g;ctx.fill();ctx.strokeStyle='#ffffff0c';ctx.lineWidth=1;ctx.stroke()}}
function icon(ctx,index,x,y,size){ctx.save();const colors=['#54be7d','#f8f3e9','#faf8ed','#182b34','#a8d2b0','#bac3c1','#fff8da','#5b9ece','#79a9ca','#d47a7e','#6ba8c7','#89948f'];roundRect(ctx,x,y,size,size,size*.24,colors[index%12]);ctx.translate(x+size/2,y+size/2);ctx.fillStyle='white';ctx.strokeStyle='white';ctx.lineWidth=size*.055;ctx.lineCap='round';const u=size;
if(index%12===0){roundRect(ctx,-u*.28,-u*.23,u*.56,u*.4,u*.14,'#fff');ctx.beginPath();ctx.moveTo(-u*.2,u*.1);ctx.lineTo(-u*.22,u*.25);ctx.lineTo(-u*.04,u*.1);ctx.fill()}
else if(index%12===1){for(let i=0;i<8;i++){ctx.save();ctx.rotate(i*Math.PI/4);ctx.fillStyle=`hsla(${i*45},65%,60%,.88)`;ctx.beginPath();ctx.ellipse(0,-u*.17,u*.12,u*.2,0,0,7);ctx.fill();ctx.restore()}}
else if(index%12===2){label(ctx,new Date().toLocaleDateString(lang==='zh'?'zh-CN':'en-US',{weekday:'short'}).toUpperCase(),0,-u*.18,u*.13,'#c65748','center',600);label(ctx,new Date().getDate(),0,u*.28,u*.48,'#303c36','center',300)}
else if(index%12===3){ctx.beginPath();ctx.arc(0,0,u*.34,0,7);ctx.stroke();ctx.beginPath();ctx.moveTo(0,-u*.23);ctx.lineTo(0,0);ctx.lineTo(u*.18,u*.1);ctx.stroke();ctx.fillStyle='#e8b66b';ctx.beginPath();ctx.arc(0,0,u*.045,0,7);ctx.fill()}
else if(index%12===4){ctx.strokeStyle='#f9f3dc';ctx.lineWidth=u*.14;ctx.beginPath();ctx.moveTo(-u*.5,u*.23);ctx.lineTo(u*.5,-u*.23);ctx.stroke();ctx.strokeStyle='#789fc8';ctx.lineWidth=u*.085;ctx.beginPath();ctx.moveTo(-u*.08,-u*.5);ctx.lineTo(u*.17,u*.5);ctx.stroke();ctx.fillStyle='#fff';ctx.beginPath();ctx.arc(0,0,u*.1,0,7);ctx.fill()}
else if(index%12===5){roundRect(ctx,-u*.33,-u*.22,u*.66,u*.46,u*.09,'#465753');ctx.strokeStyle='#bac6c0';ctx.beginPath();ctx.arc(0,0,u*.14,0,7);ctx.stroke();roundRect(ctx,-u*.2,-u*.3,u*.23,u*.1,u*.02,'#465753')}
else if(index%12===6){ctx.fillStyle='#e7c86e';ctx.fillRect(-u*.5,-u*.5,u,u*.23);ctx.strokeStyle='#c8c7ad';ctx.lineWidth=1;for(let i=0;i<3;i++){ctx.beginPath();ctx.moveTo(-u*.29,-u*.04+i*u*.15);ctx.lineTo(u*.28,-u*.04+i*u*.15);ctx.stroke()}}
else if(index%12===7){ctx.beginPath();ctx.arc(0,0,u*.33,0,7);ctx.stroke();ctx.fillStyle='#e9f1e6';ctx.beginPath();ctx.moveTo(u*.18,-u*.24);ctx.lineTo(-u*.09,u*.08);ctx.lineTo(-u*.18,u*.24);ctx.lineTo(u*.08,-u*.08);ctx.fill();ctx.fillStyle='#ce6e64';ctx.beginPath();ctx.moveTo(u*.18,-u*.24);ctx.lineTo(-u*.09,u*.08);ctx.lineTo(u*.08,-u*.08);ctx.fill()}
else if(index%12===8){ctx.strokeRect(-u*.29,-u*.2,u*.58,u*.4);ctx.beginPath();ctx.moveTo(-u*.29,-u*.2);ctx.lineTo(0,u*.03);ctx.lineTo(u*.29,-u*.2);ctx.stroke()}
else if(index%12===10){roundRect(ctx,-u*.3,-u*.2,u*.6,u*.43,u*.04,'#e0eef0');roundRect(ctx,-u*.3,-u*.27,u*.25,u*.15,u*.025,'#e0eef0')}
else label(ctx,index%12===9?'♫':'⚙',0,u*.23,u*.67,'#fff','center',400);ctx.restore()}
// Fill only the transparent outside corners once, so the CSS screen mask is
// the sole rounded edge, including when fullscreen removes that mask.
function prepareScreenTexture(img){
 const canvas=document.createElement('canvas');canvas.width=img.naturalWidth||img.width;canvas.height=img.naturalHeight||img.height;
 const ctx=canvas.getContext('2d');ctx.drawImage(img,0,0);
 const pixels=ctx.getImageData(0,0,canvas.width,canvas.height),d=pixels.data,w=canvas.width;
 for(let y=0;y<canvas.height;y++){
  const row=y*w*4;let left=0,right=w-1;
  while(left<w&&d[row+left*4+3]<255)left++;
  while(right>=left&&d[row+right*4+3]<255)right--;
  if(left>right)continue;
  for(let x=0;x<left;x++)d.set(d.subarray(row+left*4,row+left*4+4),row+x*4);
  for(let x=right+1;x<w;x++)d.set(d.subarray(row+right*4,row+right*4+4),row+x*4);
 }
 ctx.putImageData(pixels,0,0);return canvas;
}
function loadDefaultScreen(){
 const desktop=viewMode==='desktop',key=desktop?`desktop-${lang}`:`phone-${lang}`;
 if((desktop&&!desktopScreens)||defaultImages[key]||defaultLoading[key])return;
 const img=new Image();defaultLoading[key]=true;screenLoadFailed=false;img.fetchPriority='high';
 img.onload=()=>{defaultImages[key]=prepareScreenTexture(img);if(useDefaultScreen)drawScene()};
 img.onerror=()=>{defaultLoading[key]=false;screenLoadFailed=true;if(useDefaultScreen)drawScene()};
 img.src=desktop?desktopScreens[lang]:(phoneScreens?.[lang]||'./assets/demo-screen.svg');
}
function drawImageScene(img,w,h){
 const installed=viewMode==='phone'&&appleTouch&&(navigator.standalone===true||matchMedia('(display-mode: standalone)').matches);
 const immersed=document.body.classList.contains('immersed');
 const inset=Number.parseFloat(getComputedStyle(safeProbe).paddingTop)||0;
 const layout=screenLayout({iw:img.width,ih:img.height,w,h,immersed,installed,hasStatus:imageHasStatus,safeTop:inset});
 const {sx,sy,sw,sh,x,y,width,height}=layout;
 c.imageSmoothingQuality='high';
 // Extend the image's edge colors into any letterbox space. The actual image
 // keeps its aspect ratio, including on a different iPhone or in landscape.
 c.drawImage(img,0,sy,img.width,1,0,0,w,h/2);
 c.drawImage(img,0,img.height-1,img.width,1,0,h/2,w,h-h/2);
 c.drawImage(img,sx,sy,sw,sh,x,y,width,height);
}
function drawScene(){
 updateMacGeometry();
 const r={width:stage.clientWidth,height:stage.clientHeight};if(r.width<1||r.height<1)return;
 // Retain Retina detail in small previews and native phone/fullscreen views.
 // Cached Gaussian levels remain capped separately; this only raises the clear image resolution.
 const scale=Math.min(Math.max(devicePixelRatio||1,2),3,Math.sqrt(8294400/(r.width*r.height)),(gl?gl.getParameter(gl.MAX_TEXTURE_SIZE):4096)/Math.max(r.width,r.height));
 source.width=surface.width=Math.round(r.width*scale);source.height=surface.height=Math.round(r.height*scale);
 const w=r.width,h=r.height;renderWidth=stage.clientWidth;renderHeight=stage.clientHeight;
 c.setTransform(source.width/w,0,0,source.height/h,0,0);
 const img=useDefaultScreen?(viewMode==='phone'?defaultImages[`phone-${lang}`]:defaultImages[`desktop-${lang}`]):photo;
 const pending=useDefaultScreen&&!img&&(viewMode==='phone'||(desktopScreens&&!screenLoadFailed));
 stage.classList.toggle('screen-pending',pending);
 $('#screen-loading').hidden=!pending;$('#screen-loading').disabled=!screenLoadFailed;
 $('#screen-loading').textContent=t(screenLoadFailed?'screenRetry':'screenLoading');
 // Never substitute the old simulated desktop while the default image loads.
 if(pending){c.fillStyle='#f5f5f7';c.fillRect(0,0,w,h);uploadTexture();render();return}
 if(img){drawImageScene(img,w,h);uploadTexture();render();return}
 if(viewMode==='desktop'){drawDesktop(c,w,h,{lang,wall:useDefaultScreen?'coast':wall});uploadTexture();render();return}
 wallpaper(c,w,h,wall);const shade=c.createLinearGradient(0,0,0,h);shade.addColorStop(0,'#041c1720');shade.addColorStop(1,'#041c170b');c.fillStyle=shade;c.fillRect(0,0,w,h);drawPhone(w,h);uploadTexture();render();
}
function drawPhone(w,h){const scale=w/370;c.save();c.scale(scale,scale);const H=h/scale,W=370,now=new Date();label(c,now.toLocaleTimeString('en-GB',{hour:'2-digit',minute:'2-digit'}),185,29,12,'#fff','center',600);label(c,'▮▮▮  ◔  ▰',W-25,29,11,'#fff','right');const start=69,cardW=145;if(H>=350){for(const x of [30,195]){roundRect(c,x,start,cardW,101,21,'#f0f1df30');c.strokeStyle='#ffffff24';c.lineWidth=1;c.stroke()}label(c,lang==='zh'?'海岸':'COAST',45,start+24,10,'#ffffffc0');label(c,'26°',45,start+67,37,'#fff','left',250);label(c,t('weather'),45,start+87,10,'#fff');c.beginPath();c.arc(142,start+54,14,0,7);c.fillStyle='#fff1b0';c.fill();label(c,now.toLocaleDateString(lang==='zh'?'zh-CN':'en-US',{month:'short',weekday:'short'}),210,start+26,10,'#ffffffbf');label(c,now.getDate(),210,start+78,47,'#fff','left',250);}const rows=H<430?1:H<520?2:3,gridTop=H<350?70:193;const iconSize=48,gapY=Math.max(73,Math.min(86,(H-292)/3));for(let i=0;i<rows*4;i++){const x=31+(i%4)*84,y=gridTop+Math.floor(i/4)*gapY;icon(c,i,x,y,iconSize);label(c,t('apps')[i],x+24,y+64,9,'#fff','center',450)}const dockY=H-84;roundRect(c,22,dockY,326,64,23,'#e2ebe13a');for(let i=0;i<4;i++)icon(c,[0,7,8,9][i],37+i*78,dockY+9,46);c.restore()}
let gl,program,texture,uniforms,glassBlur,desktopBlur,glassTexture;
try{gl=surface.getContext('webgl2',{alpha:false,antialias:false,depth:false,powerPreference:'low-power'});if(!gl)throw Error();const compile=(type,code)=>{const s=gl.createShader(type);gl.shaderSource(s,code);gl.compileShader(s);if(!gl.getShaderParameter(s,gl.COMPILE_STATUS))throw Error(gl.getShaderInfoLog(s));return s};program=gl.createProgram();gl.attachShader(program,compile(gl.VERTEX_SHADER,`#version 300 es
out vec2 uv;void main(){vec2 p=vec2(float((gl_VertexID<<1)&2),float(gl_VertexID&2));uv=p;gl_Position=vec4(p*2.-1.,0,1);}`));gl.attachShader(program,compile(gl.FRAGMENT_SHADER,previewFragment(referenceFragment)));gl.linkProgram(program);if(!gl.getProgramParameter(program,gl.LINK_STATUS))throw Error(gl.getProgramInfoLog(program));gl.useProgram(program);uniforms=Object.fromEntries(['desktopProjection','desktopDepth','size','viewport','imageSize','uvScale','rotation','horizontal','moveRight','isCover','innerReveal','referenceGlass','opening','crease','frost','frostGradient','glassSigma','tilt','brightness','innerProgress','edgeDeformation','photo','glassPhoto'].map(k=>[k,gl.getUniformLocation(program,k)]));texture=gl.createTexture();gl.bindTexture(gl.TEXTURE_2D,texture);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MIN_FILTER,gl.LINEAR_MIPMAP_LINEAR);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MAG_FILTER,gl.LINEAR);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_S,gl.CLAMP_TO_EDGE);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_T,gl.CLAMP_TO_EDGE);glassBlur=new GlassBlur(gl);desktopBlur=new DesktopBlur(gl)}catch(e){gl=null;surface.hidden=true;surface.style.display='none';setTimeout(()=>toast('gpuFallback'),800)}
function uploadTexture(){if(!gl){if(viewMode==='desktop')captureDesktopFallback(source);return;}gl.activeTexture(gl.TEXTURE0);gl.bindTexture(gl.TEXTURE_2D,texture);gl.texImage2D(gl.TEXTURE_2D,0,gl.RGBA,gl.RGBA,gl.UNSIGNED_BYTE,source);gl.generateMipmap(gl.TEXTURE_2D);glassTexture=(viewMode==='desktop'?desktopBlur:glassBlur).build(texture,source.width,source.height)}
let visibleLidFace=null,macDeckAngle=72,desktopLayout={width:1,height:1};
function updateMacGeometry(){
 // Read the responsive deck angle when layout changes, not every animation frame.
 const rootStyle=getComputedStyle(document.documentElement);
 const value=parseFloat(rootStyle.getPropertyValue('--mac-deck-angle'));
 macDeckAngle=Number.isFinite(value)?value:72;
 const style=getComputedStyle(stage),perspective=parseFloat(getComputedStyle($('.phone-surround')).perspective)||0;
 desktopLayout={width:Math.max(1,stage.clientWidth),height:Math.max(1,stage.clientHeight),
  borderLeft:parseFloat(style.borderLeftWidth)||0,borderRight:parseFloat(style.borderRightWidth)||0,
  borderTop:parseFloat(style.borderTopWidth)||0,borderBottom:parseFloat(style.borderBottomWidth)||0,
  perspective,gap:perspective>0?4:0,immersed:document.body.classList.contains('immersed')};
}
matchMedia('(max-width:600px), (pointer:coarse) and (max-width:1024px)').addEventListener('change',()=>{updateMacGeometry();render()});
motionPreference.addEventListener('change',()=>{reduceMotion=motionPreference.matches;stopAngleMotion();drawScene()});
function render(){
 // Keep input signs intact: positive phone input now uses the opposite glass edge.
 const effectAngle=angle;
 const lidAngle=viewMode==='desktop'?-Math.abs(angle)/120*(180-macDeckAngle):0;
 document.documentElement.style.setProperty('--lid-angle',`${lidAngle}deg`);
 // A parallel projection keeps mobile faces aligned without nested 3D layers.
 document.documentElement.style.setProperty('--lid-scale',Math.cos(lidAngle*Math.PI/180));
 // WebKit can composite a transformed child of a hidden backface in front of
 // the screen. Cull the two faces explicitly, without changing their layout.
 const face=lidAngle < -90 ? 'back' : 'front';
 if(face!==visibleLidFace){
  visibleLidFace=face;
  document.documentElement.style.setProperty('--lid-front-visibility',face==='front'?'visible':'hidden');
  document.documentElement.style.setProperty('--lid-back-visibility',face==='back'?'visible':'hidden');
 }
 brightnessLevel=nextBrightness(brightnessLevel,viewMode==='desktop'?angle*.75:angle);if(gl){
 gl.useProgram(program);gl.activeTexture(gl.TEXTURE0);gl.bindTexture(gl.TEXTURE_2D,texture);gl.activeTexture(gl.TEXTURE1);gl.bindTexture(gl.TEXTURE_2D,glassTexture);
 const f=(k,v)=>gl.uniform1f(uniforms[k],v),i=(k,v)=>gl.uniform1i(uniforms[k],v),v=(k,x,y)=>gl.uniform2f(uniforms[k],x,y);
 if(viewMode==='desktop'){
  const optics=desktopOptics({...desktopLayout,angle:reduceMotion&&!desktopLayout.immersed?0:lidAngle});
  gl.uniformMatrix3fv(uniforms.desktopProjection,false,optics.projection);
  v('desktopDepth',...optics.depth);
 }
 i('photo',0);i('glassPhoto',1);v('size',surface.width,surface.height);v('viewport',surface.width,surface.height);v('imageSize',source.width,source.height);v('uvScale',1,1);
 i('rotation',0);i('horizontal',viewMode==='desktop'?1:0);i('moveRight',viewMode==='desktop'?0:effectAngle<0?1:0);i('isCover',1);i('innerReveal',0);i('referenceGlass',1);
 f('opening',Math.abs(angle)/(viewMode==='desktop'?120:90));f('crease',viewMode==='desktop'?1:effectAngle<0?0:1);f('frost',blurStrength);f('frostGradient',1);f('glassSigma',glassBlur.baseSigma);
 f('tilt',Math.abs(angle)*100/(viewMode==='desktop'?120:90)*Math.PI/180);f('brightness',brightnessLevel);f('innerProgress',1);f('edgeDeformation',compression);
 gl.viewport(0,0,surface.width,surface.height);gl.drawArrays(gl.TRIANGLES,0,3);
 }else if(viewMode==='desktop'){
  source.style.transform='none';source.style.filter='none';
  const optics=desktopOptics({...desktopLayout,angle:reduceMotion&&!desktopLayout.immersed?0:lidAngle});
  renderDesktopFallback(source,optics,blurStrength,{immersed:desktopLayout.immersed});
 }else{source.style.transformOrigin='center center';source.style.transform=`perspective(1800px) rotateY(${effectAngle*compression}deg)`;source.style.filter=`blur(${Math.abs(angle)*.13*blurStrength}px) brightness(${brightnessLevel})`}
}
// Render at the browser's native refresh cadence, independently of sensor arrival times.
function animate(timestamp){raf=0;const dt=Math.min(64,Math.max(0,timestamp-lastFrame));lastFrame=timestamp;angle=reduceMotion?target:smoothAngle(angle,target,dt,35);if(Math.abs(target-angle)<.01)angle=target;render();if(angle!==target&&!document.hidden)raf=requestAnimationFrame(animate)}
function scheduleFrame(){if(!raf&&!document.hidden){lastFrame=performance.now();raf=requestAnimationFrame(animate)}}
function updateAngleControls(){ $('#angle').value=target;$('#angle-value').value=`${Math.round(target)}°`;$('#angle-badge').textContent=`${Math.round(target)}°`; }
function stopAngleMotion(){cancelAnimationFrame(raf);raf=0;target=angle;updateAngleControls()}
function setAngle(value,{immediate=false}={}){const next=clampPreviewAngle(value,viewMode);if(next===target&&angle===target)return;if(next!==target){target=next;updateAngleControls()}if(immediate){cancelAnimationFrame(raf);raf=0;angle=target;render()}else scheduleFrame()}
function updateBlur(){const value=Math.round(blurStrength*100);$('#blur').value=value;$('#blur-value').value=`${value}%`;}
$('#blur').oninput=e=>{stopDemo();userInteracted=true;blurStrength=Number(e.target.value)/100;updateBlur();scheduleFrame();try{localStorage.setItem('foldlight-blur',String(blurStrength))}catch{}};
updateBlur();
function updateCompression(){const value=Math.round(compression*100);$('#compression').value=value;$('#compression-value').value=`${value}%`}
$('#compression').oninput=e=>{stopDemo();userInteracted=true;compression=Number(e.target.value)/100;updateCompression();scheduleFrame();try{localStorage.setItem('foldlight-compression',String(compression))}catch{}};
$('#defaults').onclick=()=>{blurStrength=effectDefaults.blur;compression=effectDefaults.compression;updateBlur();updateCompression();scheduleFrame();try{localStorage.setItem('foldlight-blur',String(blurStrength));localStorage.setItem('foldlight-compression',String(compression))}catch{}toast('defaultsRestored')};
updateCompression();
function reset(){stopDemo();userInteracted=true;motionReference=lastOrientation;setAngle(0);toast('calibrated')}
function translate(){document.title=t('pageTitle');$('meta[name="description"]').content=t('pageDescription');$$('[data-href-en]').forEach(el=>el.setAttribute('href',lang==='en'?el.dataset.hrefEn:el.dataset.hrefZh));$('#status-fit').checked=imageHasStatus;$('#status-fit-options').hidden=viewMode==='desktop'||(!useDefaultScreen&&!photo);document.documentElement.lang=lang==='zh'?'zh-CN':'en';$$('[data-i18n]').forEach(el=>el.textContent=t(el.dataset.i18n));$$('[data-label]').forEach(el=>{el.setAttribute('aria-label',t(el.dataset.label));if(el.tagName==='BUTTON')el.title=t(el.dataset.label)});$('#language').textContent=lang==='zh'?'EN':'ZH';$('#language').setAttribute('aria-label',lang==='zh'?'Switch to English':'Switch to Chinese');$$('[data-wall]').forEach(el=>el.setAttribute('aria-pressed',String(!useDefaultScreen&&!photo&&el.dataset.wall===wall)));updateMotionUI();updatePresentation();$('#screen-label').textContent=t(photo?'customScreen':viewMode==='desktop'?'desktopScreen':useDefaultScreen?'realScreen':'simulated');$('#default-screen').setAttribute('aria-pressed',String(useDefaultScreen));loadDefaultScreen();drawScene();updateFullscreenGuide()}
$('#language').onclick=()=>{lang=lang==='zh'?'en':'zh';try{localStorage.setItem('foldlight-language',lang)}catch{}translate()};
$('#angle').oninput=e=>{stopDemo();userInteracted=true;stopMotion();setAngle(e.target.value)};$('#reset').onclick=reset;
function immerse(value){stopDemo();userInteracted=true;document.body.classList.toggle('immersed',value);document.querySelector('meta[name="theme-color"]').content=value?'#071318':'#f5f5f7';requestAnimationFrame(drawScene)}
async function leaveFullscreen(){if(document.fullscreenElement){try{await document.exitFullscreen()}catch{}}immerse(false)}
async function enterFullscreen(){
 if(standalone()){immerse(true);return}
 try{if(!document.documentElement.requestFullscreen)throw Error();await document.documentElement.requestFullscreen();immerse(true)}
 catch{immerse(true);toast('fullFallback')}
}
$('#hide').onclick=()=>{$('#settings-dialog').close();immerse(true)};$('#restore').onclick=leaveFullscreen;
$('#fullscreen-guide').onclick=showFullscreenGuide;
$('#settings-toggle').onclick=()=>{$('#settings-dialog').showModal()};
$('#guide-start').onclick=()=>{$('#fullscreen-dialog').close();enterFullscreen()};
$('#fullscreen').onclick=()=>{if(document.fullscreenElement){leaveFullscreen();return}if(appleTouch&&!standalone()&&!document.fullscreenEnabled){showFullscreenGuide();return}enterFullscreen()};
document.addEventListener('fullscreenchange',()=>{if(!document.fullscreenElement)immerse(false)});
document.addEventListener('keydown',e=>{if(e.key==='Escape'&&!$$('dialog[open]').length)leaveFullscreen()});
for(const id of ['wallpaper','about'])$('#'+id).onclick=()=>$('#'+id+'-dialog').showModal();$$('.close').forEach(el=>el.onclick=()=>el.closest('dialog').close());$$('dialog').forEach(d=>d.addEventListener('click',e=>{const r=d.getBoundingClientRect();if(e.target===d&&(e.clientX<r.left||e.clientX>r.right||e.clientY<r.top||e.clientY>r.bottom))d.close()}));
$$('[data-wall]').forEach(el=>{wallpaper(el.querySelector('canvas').getContext('2d'),260,180,el.dataset.wall);el.onclick=()=>{++uploadRevision;useDefaultScreen=false;wall=el.dataset.wall;photo=null;translate();$('#wallpaper-dialog').close()}});
$('#default-screen').onclick=()=>{++uploadRevision;useDefaultScreen=true;photo=null;imageHasStatus=true;translate();$('#wallpaper-dialog').close()};
$('#screen-loading').onclick=()=>{loadDefaultScreen();drawScene()};
$('#status-fit').onchange=e=>{imageHasStatus=e.target.checked;drawScene()};
$('#upload').onclick=()=>$('#file').click();$('#file').onchange=async e=>{const file=e.target.files?.[0];e.target.value='';if(!file)return;if(file.size>25*1024*1024){toast('imageLarge');return}if(!file.type.startsWith('image/')){toast('imageError');return}const revision=++uploadRevision;toast('imageBusy');const url=URL.createObjectURL(file);try{const img=new Image();img.src=url;await img.decode();if(!img.naturalWidth)throw Error();const cap=Math.min(1,2560/Math.max(img.naturalWidth,img.naturalHeight)),out=document.createElement('canvas');out.width=Math.round(img.naturalWidth*cap);out.height=Math.round(img.naturalHeight*cap);out.getContext('2d').drawImage(img,0,0,out.width,out.height);if(revision!==uploadRevision)return;photo=prepareScreenTexture(out);imageHasStatus=isPhoneScreenshot(img.naturalWidth,img.naturalHeight,screen.width,screen.height);useDefaultScreen=false;translate();$('#wallpaper-dialog').close();toast('imageOK')}catch{if(revision===uploadRevision)toast('imageError')}finally{URL.revokeObjectURL(url)}};
let drag=null;
const dragSurface=$('.phone-surround');
dragSurface.addEventListener('pointerdown',e=>{
 if(e.button>0||e.isPrimary===false||drag||e.target.closest('button'))return;
 stopDemo();if(viewMode==='desktop'){stopAngleMotion();e.preventDefault()}
 dragSurface.setPointerCapture(e.pointerId);
 drag={pointerId:e.pointerId,x:e.clientX,y:e.clientY,angle:viewMode==='desktop'?angle:target,moved:false};
});
dragSurface.addEventListener('pointermove',e=>{
 if(!drag||e.pointerId!==drag.pointerId)return;
 if(e.pointerType==='mouse'&&e.buttons===0){endDrag(e);return}
 const dx=e.clientX-drag.x,dy=e.clientY-drag.y;
 if(Math.hypot(dx,dy)>5)drag.moved=true;
 if(!drag.moved)return;
 if(viewMode==='phone'&&Math.abs(dy)>Math.abs(dx)&&!document.body.classList.contains('immersed'))return;
 userInteracted=true;stopMotion();
 // Direct manipulation stays under the pointer, and stops on the exact pose
 // visible at release. Positive screen Y closes a desktop lid.
 setAngle(drag.angle+(viewMode==='desktop'?-dy*.34:dx*.35),{immediate:viewMode==='desktop'});
});
function endDrag(e,{allowTap=false}={}){
 if(!drag||e.pointerId!==drag.pointerId)return;
 const tap=!drag.moved;drag=null;
 if(viewMode==='desktop')stopAngleMotion();
 if(dragSurface.hasPointerCapture(e.pointerId))dragSurface.releasePointerCapture(e.pointerId);
 if(allowTap&&tap&&document.body.classList.contains('immersed'))leaveFullscreen();
}
dragSurface.addEventListener('pointerup',e=>endDrag(e,{allowTap:true}));
dragSurface.addEventListener('pointercancel',endDrag);
dragSurface.addEventListener('lostpointercapture',endDrag);
window.addEventListener('blur',()=>{if(drag)endDrag({pointerId:drag.pointerId})});

function stopMotion(key=physicalDevice==='phone'?'idle':'desktopHint'){motionRequest++;motionEnabled=false;motionReceived=false;clearInterval(motionTimer);window.removeEventListener('deviceorientation',orientation);statusKey=key;updateMotionUI()}
function orientation(event){if(!motionEnabled||document.hidden)return;const value=motionValue(event,screen.orientation?.angle??window.orientation??0);if(value===null)return;motionLastAt=performance.now();if(!motionReceived){motionReceived=true;statusKey='active';updateMotionUI()}lastOrientation=value;if(motionReference===null)motionReference=value;setAngle(value-motionReference)}
$('#motion').onclick=async()=>{
 stopDemo();userInteracted=true;if(motionEnabled){stopMotion();return}if(physicalDevice!=='phone'){playDemo();return}
 if(!window.isSecureContext){stopMotion('secure');return}
 const revision=++motionRequest;$('#motion').disabled=true;
 try{
  if(typeof DeviceOrientationEvent==='undefined'){stopMotion('missing');return}
  if(typeof DeviceOrientationEvent.requestPermission==='function'){
   const permission=await DeviceOrientationEvent.requestPermission();if(revision!==motionRequest)return;
   if(permission!=='granted'){stopMotion('denied');return}
  }
  if(revision!==motionRequest)return;
  motionReference=null;lastOrientation=null;motionLastAt=performance.now();motionEnabled=true;motionReceived=false;statusKey='waiting';
  window.addEventListener('deviceorientation',orientation);updateMotionUI();
  motionTimer=setInterval(()=>{if(!document.hidden&&performance.now()-motionLastAt>4000)stopMotion('missing')},1000);
 }catch{stopMotion('denied')}finally{$('#motion').disabled=false}
};
document.addEventListener('visibilitychange',()=>{if(document.hidden){stopDemo();if(drag)endDrag({pointerId:drag.pointerId});if(viewMode==='desktop')stopAngleMotion();else{cancelAnimationFrame(raf);raf=0}}else{motionReference=null;motionLastAt=performance.now();drawScene();if(angle!==target)scheduleFrame()}});
surface.addEventListener('webglcontextlost',e=>{e.preventDefault();cancelAnimationFrame(raf);raf=0;gl=null;surface.style.display='none';render();toast('gpuFallback')});surface.addEventListener('webglcontextrestored',()=>location.reload());
function updatePresentation(){
 const desktop=viewMode==='desktop';
 document.documentElement.dataset.view=viewMode;
 $('#compression').closest('.slider-control').hidden=desktop;
 $('#hero-line').textContent=t(desktop?'desktopLine':'phoneLine');
 $('#hero-description').textContent=t(desktop?'desktopDescription':'phoneDescription');
 $('#scene-note').textContent=t(desktop?'desktopNote':physicalDevice==='phone'?'phoneHint':'phoneDesktopNote');
 $('#feature1-copy').textContent=t(desktop?'feature1Desktop':'feature1Phone');
 $('#default-screen').textContent=t(desktop?'desktopDefault':'defaultScreen');
 surface.setAttribute('aria-label',t(desktop?'desktopCanvas':'canvas'));
 $('label[for="angle"]').textContent=t(desktop?'desktopAngle':'angle');
 $('#angle').min=desktop?-120:-90;$('#angle').max=desktop?0:90;
 const labels=$('#angle').nextElementSibling;labels.firstElementChild.textContent=desktop?'−120°':'−90°';labels.lastElementChild.textContent=desktop?'0°':'+90°';
 $('#motion-status').textContent=t(desktop?'desktopHint':physicalDevice==='desktop'?'phoneDesktopNote':statusKey);
 $$('[data-mode]').forEach(button=>button.setAttribute('aria-pressed',String(button.dataset.mode===viewMode)));
}
function stopDemo(){demoRevision++;cancelAnimationFrame(demoFrame);demoFrame=0;}
function playDemo(){
 stopDemo();stopMotion();userInteracted=true;
 const revision=demoRevision,start=performance.now(),from=target,extent=viewMode==='desktop'?-120:38;
 if(reduceMotion){setAngle(extent);return}
 const ease=x=>x*x*(3-2*x);
 function tick(now){if(revision!==demoRevision||document.hidden)return;const p=Math.min(1,(now-start)/(viewMode==='desktop'?3300:2600));const pose=p<.5?from+(extent-from)*ease(p*2):extent*(1-ease((p-.5)*2));setAngle(pose);if(p<1)demoFrame=requestAnimationFrame(tick);else demoFrame=0;}
 demoFrame=requestAnimationFrame(tick);
}
$('#demo').onclick=playDemo;
$$('[data-mode]').forEach(button=>button.onclick=()=>{if(viewMode===button.dataset.mode)return;stopDemo();stopMotion();viewMode=button.dataset.mode;userInteracted=true;angle=target=0;brightnessLevel=NaN;$('#angle').value=0;$('#angle-value').value='0°';$('#angle-badge').textContent='0°';translate()});
let resizeFrame=0;
new ResizeObserver(()=>{cancelAnimationFrame(resizeFrame);resizeFrame=requestAnimationFrame(drawScene)}).observe(stage);
translate();
installPageMotion({onProgress:progress=>{if(viewMode==='desktop'&&!motionEnabled&&!drag&&!demoFrame&&!$('dialog[open]')&&!document.body.classList.contains('immersed'))setAngle(scrollPreviewAngle(progress))}});
setInterval(()=>{if(!document.hidden)drawScene()},60000);
