折光 · Android Fold — 全局版
0.2.4

全局版目前仅在 Samsung Fold8（SM-F9710）实测；Fold7 的全局效果及其他机型未测试，当前构建不启用这些机型的双屏会话。

Beta · 已验证浏览器场景。动画期间画面短暂定格，不是实时视频。暂不支持桌面、最近任务、横屏及受保护页面。

全局版 · 安装与使用
1. 先暂停图片版的全屏效果，再安装“折光·全局实验”。两款 App 的图片、参数与授权相互独立。
2. 先启动 Shizuku，再打开全局版，点“1 授权连续角度”并允许它的独立授权。
3. 点“2 授权屏幕效果”，在系统无障碍设置的已安装应用中启用“折光·全局实验”，然后返回 App 开启全局效果。
4. 完全合拢一次，解锁外屏并打开浏览器，再缓慢展开。设置页提供六项参数、自动保存和恢复默认。

Shizuku: https://shizuku.rikka.app/zh-hans/guide/setup/

六项参数：外屏展开变暗 / 折叠变亮角度，内屏左侧展开变亮 / 折叠变暗角度，外屏右侧压缩和模糊程度。支持自动保存及恢复默认；内屏右半边保持清晰、静止。

停止方式：回到全局版设置页点“暂停全局效果”，或锁屏。画面中没有悬浮按钮。配置完成后可断开 USB；手机重启后需重新启动 Shizuku。

Shizuku 读取屏幕并迁移当前 App，无障碍服务显示效果层。截图只在本机内存中处理，不保存、不上传。双屏持续点亮会增加耗电。

默认参数：外屏 80° / 90°；内屏左侧 55° / 70°；右侧压缩 60%；模糊 55%。设置只保存在各自 App 中。

故障排查：确认机型在上述测试范围内；确认 Shizuku 正在运行、当前 App 已单独授权；全局版还需开启无障碍服务。若没有效果，先暂停，完全合拢后重新解锁，再开启体验。系统更新可能影响兼容性。

这是可直接下载的测试 APK，当前使用开发签名。保留现有签名用于覆盖升级；自行构建的 APK 使用你自己的签名，不能保证直接覆盖本网站版本。

Source: https://github.com/win223909/foldlight
Website: https://duo.zksaga.com/#android-fold

Credits: architecture and adapted capture/window-readiness helpers from bunkaich/Folduo (MIT). Copyright (c) 2026 bunkaich. License included in the app and source repository.
https://github.com/bunkaich/Folduo
Made by ZK
