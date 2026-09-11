# Foldlight · 折光

[English](README.md) · [在线体验与下载](https://duo.zksaga.com/) · [构建指南](docs/BUILDING.md) · [架构说明](docs/ARCHITECTURE.md)

让透视、磨砂玻璃与光影，跟随屏幕的转动和开合。

基于 [Elijah Semyonov 的 DuoLikeAnimation](https://github.com/elijah-semyonov/DuoLikeAnimation) 扩展，现包含网页、iPhone、Mac 和 Fold8 四个平台。

| 平台 | 功能 | 要求 |
| --- | --- | --- |
| 网页 | 转动手机或拖动图片；自选图片、中英文、全屏引导 | 支持 WebGL 2 的现代浏览器；手机感应需要 HTTPS |
| iPhone | 照片透视与磨砂，随手机姿态变化 | iOS 26.5+，当前 1.0.3 |
| Mac | 后台菜单栏 App，实时桌面合盖效果 | macOS 13+，Apple Silicon / Intel，当前 0.2.4 |
| Fold8 | 内外屏独立图片，连续角度跟随，展开与折叠独立调节 | 已实测 SM-F9710 / Android 17 / One UI 9，当前 0.3.7 |

网页、iPhone 和 Android 只显示 App 自己的图片，不替换系统桌面或其他 App 的动画。Mac 读取实时桌面需要屏幕录制授权。Fold8 连续角度与双屏运行需要 Shizuku 授权和兼容固件，不保证所有折叠设备均可用。

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

## Fold8 设置

按「外屏 → 展开 / 折叠」「内屏左半边 → 展开 / 折叠」「共用」排列。每个方向分别调整开始变形角度、速度、幅度、加速度和亮度起点；两块屏幕分别设置 0–100% 磨砂及渐变强度。内屏右半边始终清晰、静止。双指长按约 0.65 秒呼出设置。

## 目录与验证

`web/` 为网页；`DuoLikeAnimation/` 为 iPhone；`macos/` 为 Mac；`android/` 为 Fold8；`analytics/` 是可选的受保护访问统计模块。文档在 `docs/`，自动化检查在 `.github/`。

CI 执行网页测试与构建、Android 单元测试/lint/构建、统计模块测试、Mac 通用构建及 iOS 模拟器构建。连续开合手感和 GPU 检查仍需真机，不能用 CI 通过替代。详见 [兼容性说明](docs/COMPATIBILITY.md)。

公开源码使用自制矢量示例。个人壁纸、第三方桌面截图、部署密钥、签名文件、访问明细和真机日志不进入仓库；线上旧版示例素材可能与源码不同。

## 参与和授权

[贡献指南](CONTRIBUTING.md) · [安全报告](SECURITY.md) · [更新记录](CHANGELOG.md)

MIT 许可，保留原作者 Elijah Semyonov 与 ZK 的版权声明。依赖与素材说明见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。项目与 Apple、Samsung、Shizuku 无隶属关系。

Made by **ZK**.
