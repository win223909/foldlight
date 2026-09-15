# Foldlight · 折光

[English](README.md) · [在线体验与下载](https://duo.zksaga.com/) · [构建指南](docs/BUILDING.md) · [架构说明](docs/ARCHITECTURE.md)

让透视、磨砂玻璃与光影，跟随屏幕的转动和开合。

基于 [Elijah Semyonov 的 DuoLikeAnimation](https://github.com/elijah-semyonov/DuoLikeAnimation) 扩展，现包含网页、iPhone、Mac 和 Android Fold 多端版本。

| 平台 | 功能 | 要求 |
| --- | --- | --- |
| 网页 | 转动手机或拖动图片；自选图片、中英文、全屏引导 | 支持 WebGL 2 的现代浏览器；手机感应需要 HTTPS |
| iPhone | 照片透视与磨砂，随手机姿态变化 | iOS 26.5+，当前 1.0.3 |
| Mac | 后台菜单栏 App，实时桌面合盖效果 | macOS 13+，Apple Silicon / Intel，当前 0.2.6 |
| Android Fold · 图片版 | 内外屏独立图片，连续角度跟随，六项效果参数 | 已实测 Samsung Fold7、Fold8；0.3.8-fold7.1 |
| Android Fold · 全局版 | 在兼容 App 中用当前页面截图呈现翻折过渡 | 仅实测 Samsung Fold8；0.2.4 Beta |

其他 Android 机型及固件未测试。全局版当前不会在 Fold7 启用双屏会话。两款 Android App 独立安装，请勿同时开启双屏效果。

网页、iPhone 和 Android 图片版呈现自己的图片。全局版在本机内存中读取兼容 App 的截图，动画时画面短暂定格，不是实时视频；暂不支持桌面、最近任务、横屏和受保护页面。Android 需要分别授予 Shizuku 权限；全局版还需启用无障碍服务。Mac 实时桌面需要屏幕录制授权。

[图片版使用说明](docs/android-fold/fold-zh-CN.md) · [全局版使用说明](docs/android-fold/global-zh-CN.md) · [下载两款 App](https://duo.zksaga.com/#android-fold)

## 快速开始

```sh
git clone https://github.com/win223909/foldlight.git
cd foldlight
npm run dev
# 打开 http://127.0.0.1:4178
npm test
npm run build
npm run check:public
```

网页无需安装 npm 依赖，使用 Node.js 20+。原生端的构建命令、签名和硬件检查见 [构建指南](docs/BUILDING.md)。安装包不存入 Git；网页下载入口指向已经托管的版本化文件。

## Android Fold 设置

两款 App 均保留六项调节：外屏展开变暗 / 折叠变亮角度、内屏左侧展开变亮 / 折叠变暗角度、外屏右侧压缩程度和模糊程度。自动保存，支持恢复默认。内屏右半边保持清晰、静止。图片版双指长按呼出设置；全局版回到自己的设置页调节，不显示悬浮按钮。

## 目录与验证

`web/` 为网页；`DuoLikeAnimation/` 为 iPhone；`macos/` 为 Mac；`android/` 为 Android 图片版；`android-global/` 为独立的全局版。文档在 `docs/`，自动化检查在 `.github/`。

CI 执行网页测试与构建、Android 单元测试/lint/构建、Mac 通用构建及 iOS 模拟器构建。连续开合手感和 GPU 检查仍需真机，不能用 CI 通过替代。详见 [兼容性说明](docs/COMPATIBILITY.md)。

公开源码使用自制矢量示例。个人壁纸、第三方桌面截图、部署密钥、签名文件、访问明细和真机日志不进入仓库；线上旧版示例素材可能与源码不同。

## 参与和授权

[贡献指南](CONTRIBUTING.md) · [安全报告](SECURITY.md) · [更新记录](CHANGELOG.md)

MIT 许可，保留原作者 Elijah Semyonov 与 ZK 的版权声明。依赖与素材说明见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。项目与 Apple、Samsung、Shizuku 无隶属关系。

Made by **ZK**.
