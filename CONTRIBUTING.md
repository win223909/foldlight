# Contributing / 参与贡献

1. Open an issue for substantial behavior or architectural changes. Small fixes can go directly to a pull request.
2. Create a focused branch and keep unrelated formatting or platform changes out of the patch.
3. Run the checks for the platform you changed; see [BUILDING.md](docs/BUILDING.md).
4. Describe the problem, resulting behavior, checks performed and any physical-device limitations.
5. Keep images, signing credentials, local paths, device IDs, server addresses and visitor data out of commits. Run `npm run check:public` after staging files.

Public CI uses generated fixtures and build-only native validation. Physical Android instrumentation may temporarily change app settings and display mode; use a dedicated test device and preserve your data first. Never present emulator output as proof of hinge tracking.

贡献时请说明平台、系统版本、复现步骤、实际结果和预期结果。较大改动先提交 Issue；提交前运行对应检查和公开内容扫描。真机开合效果需要记录真实验证范围，不能仅凭目标帧率声称流畅。

Contributions are provided under the repository MIT license. Be respectful, specific and constructive in issues and reviews.

Dependency updates are opt-in: review and rename `.github/dependabot.yml.example` when ready. Native sensor paths and pinned build tools need compatibility review before upgrades.
